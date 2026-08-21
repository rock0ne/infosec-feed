using System.ComponentModel;
using System.Drawing;
using System.Windows;
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

    public MainWindow()
    {
        InitializeComponent();
        DataContext = _viewModel;
        _viewModel.AlertsRaised += OnAlertsRaised;

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

        Loaded += async (_, _) => await _viewModel.InitializeAsync();
        Closing += OnClosing;
        StateChanged += (_, _) =>
        {
            if (WindowState == WindowState.Minimized) Hide();
        };
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
        WindowState = WindowState.Normal;
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
