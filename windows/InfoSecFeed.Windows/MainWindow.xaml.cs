using System.ComponentModel;
using System.Drawing;
using System.Windows;
using System.Windows.Input;
using System.Windows.Threading;
using InfoSecFeed.Windows.Models;
using InfoSecFeed.Windows.ViewModels;
using Forms = System.Windows.Forms;

namespace InfoSecFeed.Windows;

public partial class MainWindow : Window
{
    private readonly MainViewModel _viewModel = new();
    private readonly Forms.NotifyIcon _trayIcon;
    private readonly DispatcherTimer _refreshTimer;
    private bool _exitRequested;
    private bool _trayHintShown;
    private bool _windowReady;
    private WindowState _lastVisibleWindowState = WindowState.Normal;

    public MainWindow()
    {
        InitializeComponent();
        DataContext = _viewModel;
        _viewModel.AlertsRaised += OnAlertsRaised;
        _viewModel.TextScaleChanged += (_, scale) => ApplyTextScale(scale);
        ApplyTextScale(_viewModel.TextScale);
        ApplyDesktopMode();

        _trayIcon = new Forms.NotifyIcon
        {
            Icon = SystemIcons.Shield,
            Text = "InfoSec Feed",
            Visible = true,
            ContextMenuStrip = BuildTrayMenu(),
        };
        _trayIcon.DoubleClick += (_, _) => RestoreWindow();

        _refreshTimer = new DispatcherTimer { Interval = TimeSpan.FromMinutes(30) };
        _refreshTimer.Tick += async (_, _) => await _viewModel.RefreshAsync();
        _refreshTimer.Start();

        Loaded += OnLoaded;
        Closing += OnClosing;
        StateChanged += OnWindowStateChanged;
    }

    private async void OnLoaded(object sender, RoutedEventArgs args)
    {
        if (_windowReady) return;
        await _viewModel.InitializeAsync();
        ApplyTextScale(_viewModel.TextScale);
        if (_viewModel.OpenMaximized) WindowState = WindowState.Maximized;
        _lastVisibleWindowState = WindowState == WindowState.Maximized
            ? WindowState.Maximized
            : WindowState.Normal;
        _windowReady = true;
        ApplyDesktopMode();
    }

    private void OnWindowStateChanged(object? sender, EventArgs args)
    {
        if (WindowState == WindowState.Minimized)
        {
            Hide();
            return;
        }

        _lastVisibleWindowState = WindowState;
        ApplyDesktopMode();
        if (_windowReady) _viewModel.SetOpenMaximized(WindowState == WindowState.Maximized);
    }

    private void ToggleDesktopMode_Click(object sender, RoutedEventArgs args) => ToggleDesktopMode();

    private void ToggleDesktopMode()
    {
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
    }

    private void OnPreviewKeyDown(object sender, System.Windows.Input.KeyEventArgs args)
    {
        if (args.Key == Key.F11)
        {
            ToggleDesktopMode();
            args.Handled = true;
            return;
        }

        if ((Keyboard.Modifiers & ModifierKeys.Control) == 0) return;
        switch (args.Key)
        {
            case Key.Add:
            case Key.OemPlus:
                _viewModel.IncreaseTextCommand.Execute(null);
                args.Handled = true;
                break;
            case Key.Subtract:
            case Key.OemMinus:
                _viewModel.DecreaseTextCommand.Execute(null);
                args.Handled = true;
                break;
            case Key.D0:
            case Key.NumPad0:
                _viewModel.ResetTextCommand.Execute(null);
                args.Handled = true;
                break;
        }
    }

    private static void ApplyTextScale(double requestedScale)
    {
        var scale = Services.DisplayPreferences.NormalizeTextScale(requestedScale);
        var resources = System.Windows.Application.Current.Resources;
        resources["TextXs"] = 10d * scale;
        resources["TextSmall"] = 12d * scale;
        resources["TextBody"] = 13d * scale;
        resources["TextControl"] = 14d * scale;
        resources["TextTitle"] = 18d * scale;
        resources["TextHero"] = 25d * scale;
        resources["TitleMaxHeight"] = 52d * scale;
        resources["SummaryMaxHeight"] = 56d * scale;
    }

    private void ApplyDesktopMode()
    {
        var fullDesktop = WindowState == WindowState.Maximized;
        FullDesktopButton.Content = fullDesktop ? "Restore window" : "Full desktop";
        HeaderBorder.Padding = fullDesktop ? new Thickness(40, 22, 40, 22) : new Thickness(24, 18, 24, 18);
        FilterBorder.Padding = fullDesktop ? new Thickness(40, 16, 40, 16) : new Thickness(24, 14, 24, 14);
        CardsList.Padding = fullDesktop ? new Thickness(40, 20, 40, 20) : new Thickness(20, 12, 20, 12);

        var resources = System.Windows.Application.Current.Resources;
        resources["CardImageWidth"] = fullDesktop ? 340d : 250d;
        resources["CardImageHeight"] = fullDesktop ? 200d : 155d;
        resources["CardMaxWidth"] = fullDesktop ? 1650d : 1800d;
        resources["CardContentMargin"] = fullDesktop ? new Thickness(26, 20, 26, 20) : new Thickness(20, 16, 20, 16);
    }

    private Forms.ContextMenuStrip BuildTrayMenu()
    {
        var menu = new Forms.ContextMenuStrip();
        menu.Items.Add("Open InfoSec Feed", null, (_, _) => RestoreWindow());
        menu.Items.Add("Refresh now", null, async (_, _) => await _viewModel.RefreshAsync());
        menu.Items.Add(new Forms.ToolStripSeparator());
        menu.Items.Add("Exit", null, (_, _) => ExitApplication());
        return menu;
    }

    private void OnAlertsRaised(object? sender, IReadOnlyList<FeedItem> alerts)
    {
        var first = alerts[0];
        _trayIcon.BalloonTipIcon = Forms.ToolTipIcon.Warning;
        _trayIcon.BalloonTipTitle = alerts.Count == 1
            ? $"{first.Severity}: {first.Title}"
            : $"{alerts.Count} new high-priority security items";
        _trayIcon.BalloonTipText = alerts.Count == 1
            ? first.Source
            : $"Latest: {first.Title}";
        _trayIcon.ShowBalloonTip(9000);
    }

    private void OnClosing(object? sender, CancelEventArgs args)
    {
        if (_exitRequested) return;
        args.Cancel = true;
        Hide();
        if (_trayHintShown) return;
        _trayHintShown = true;
        _trayIcon.BalloonTipTitle = "InfoSec Feed is still running";
        _trayIcon.BalloonTipText = "It will refresh securely in the background. Use the tray icon to reopen or exit.";
        _trayIcon.BalloonTipIcon = Forms.ToolTipIcon.Info;
        _trayIcon.ShowBalloonTip(6000);
    }

    private void RestoreWindow()
    {
        Show();
        WindowState = _lastVisibleWindowState;
        Activate();
    }

    private void ExitApplication()
    {
        _exitRequested = true;
        _refreshTimer.Stop();
        _viewModel.Dispose();
        _trayIcon.Visible = false;
        _trayIcon.Dispose();
        System.Windows.Application.Current.Shutdown();
    }
}
