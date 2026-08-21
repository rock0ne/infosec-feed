using System.Collections.ObjectModel;
using System.IO;
using InfoSecFeed.Windows.Models;
using InfoSecFeed.Windows.Services;

namespace InfoSecFeed.Windows.ViewModels;

public sealed class MainViewModel : BindableBase, IDisposable
{
    private readonly FeedService _feedService = new();
    private readonly ImageService _imageService = new();
    private readonly AppStorage _storage = new();
    private readonly SemaphoreSlim _settingsWriteGate = new(1, 1);
    private readonly List<FeedItem> _allItems = [];
    private CancellationTokenSource? _imageCancellation;
    private UserSettings _settings = new();
    private string _selectedCategory = Models.Categories.All;
    private string _searchText = "";
    private string _status = "Loading cached intelligence…";
    private bool _isBusy;
    private AlertMode _selectedAlertMode;
    private double _textScale = DisplayPreferences.DefaultTextScale;

    public MainViewModel()
    {
        RefreshCommand = new RelayCommand(_ => _ = RefreshAsync(), _ => !IsBusy);
        IncreaseTextCommand = new RelayCommand(_ => TextScale = DisplayPreferences.Increase(TextScale));
        DecreaseTextCommand = new RelayCommand(_ => TextScale = DisplayPreferences.Decrease(TextScale));
        ResetTextCommand = new RelayCommand(_ => TextScale = DisplayPreferences.DefaultTextScale);
    }

    public ObservableCollection<FeedCardViewModel> Items { get; } = [];
    public IReadOnlyList<string> Categories { get; } = Models.Categories.Ordered;
    public IReadOnlyList<AlertChoice> AlertChoices { get; } =
    [
        new(AlertMode.Off, "Off"),
        new(AlertMode.KevOnly, "CISA KEV only"),
        new(AlertMode.KevAndCritical, "KEV + critical CVEs"),
    ];
    public RelayCommand RefreshCommand { get; }
    public RelayCommand IncreaseTextCommand { get; }
    public RelayCommand DecreaseTextCommand { get; }
    public RelayCommand ResetTextCommand { get; }

    public string SelectedCategory
    {
        get => _selectedCategory;
        set
        {
            if (Set(ref _selectedCategory, value)) ApplyFilter();
        }
    }

    public string SearchText
    {
        get => _searchText;
        set
        {
            if (Set(ref _searchText, value)) ApplyFilter();
        }
    }

    public AlertMode SelectedAlertMode
    {
        get => _selectedAlertMode;
        set
        {
            if (Set(ref _selectedAlertMode, value))
                _ = PersistAlertSelectionAsync(value);
        }
    }

    public double TextScale
    {
        get => _textScale;
        set
        {
            var normalized = DisplayPreferences.NormalizeTextScale(value);
            if (!Set(ref _textScale, normalized)) return;
            Raise(nameof(TextScaleLabel));
            _settings = _settings with { TextScale = normalized };
            TextScaleChanged?.Invoke(this, normalized);
            _ = SaveSettingsAsync();
        }
    }

    public string TextScaleLabel => DisplayPreferences.Percentage(TextScale);

    public bool OpenMaximized => _settings.OpenMaximized;

    public string Status { get => _status; private set => Set(ref _status, value); }

    public bool IsBusy
    {
        get => _isBusy;
        private set
        {
            if (Set(ref _isBusy, value)) RefreshCommand.RaiseCanExecuteChanged();
        }
    }

    public event EventHandler<IReadOnlyList<FeedItem>>? AlertsRaised;
    public event EventHandler<double>? TextScaleChanged;

    public async Task InitializeAsync()
    {
        _settings = await _storage.LoadSettingsAsync();
        _selectedAlertMode = _settings.AlertMode;
        _textScale = _settings.TextScale;
        Raise(nameof(SelectedAlertMode));
        Raise(nameof(TextScale));
        Raise(nameof(TextScaleLabel));
        TextScaleChanged?.Invoke(this, TextScale);

        var cached = await _storage.LoadFeedAsync();
        _allItems.AddRange(cached);
        ApplyFilter();
        Status = cached.Count == 0
            ? "No cache yet — fetching security intelligence…"
            : $"{cached.Count} cached items · refreshing in the background";
        _ = RefreshAsync();
    }

    public void SetOpenMaximized(bool value)
    {
        if (_settings.OpenMaximized == value) return;
        _settings = _settings with { OpenMaximized = value };
        Raise(nameof(OpenMaximized));
        _ = SaveSettingsAsync();
    }

    public async Task RefreshAsync()
    {
        if (IsBusy) return;
        IsBusy = true;
        Status = "Refreshing 50+ security sources…";
        try
        {
            var hadSeenState = _storage.HasSeenState;
            var seen = await _storage.LoadSeenIdsAsync();
            var refreshed = await _feedService.RefreshAsync();
            if (refreshed.Count == 0)
            {
                Status = _allItems.Count == 0
                    ? "Sources are temporarily unavailable. Try again shortly."
                    : $"Refresh unavailable · showing {_allItems.Count} cached items";
                return;
            }

            var alerts = hadSeenState
                ? AlertPolicy.SelectNew(refreshed, seen, SelectedAlertMode)
                : [];
            var currentAlertIds = AlertPolicy.AlertIds(refreshed, SelectedAlertMode);

            _allItems.Clear();
            _allItems.AddRange(refreshed);
            ApplyFilter();
            await _storage.SaveFeedAsync(refreshed);
            await _storage.SaveSeenIdsAsync(currentAlertIds);

            Status = $"{refreshed.Count} items · updated {DateTime.Now:t}";
            if (alerts.Count > 0) AlertsRaised?.Invoke(this, alerts);
        }
        catch (OperationCanceledException)
        {
            Status = "Refresh cancelled";
        }
        catch (Exception error)
        {
            Status = $"Refresh failed · {error.Message}";
        }
        finally
        {
            IsBusy = false;
        }
    }

    private void ApplyFilter()
    {
        _imageCancellation?.Cancel();
        _imageCancellation?.Dispose();
        _imageCancellation = new CancellationTokenSource();

        var filtered = FeedFilter.Apply(_allItems, SelectedCategory, SearchText);
        Items.Clear();
        foreach (var item in filtered) Items.Add(new FeedCardViewModel(item));
        _ = LoadImagesAsync(Items.Take(30).ToList(), _imageCancellation.Token);
    }

    private async Task PersistAlertSelectionAsync(AlertMode mode)
    {
        // Seed the newly selected threshold from the current feed. Enabling or
        // widening alerts must never dump historical items into the tray.
        _settings = _settings with { AlertMode = mode };
        await SaveSettingsAsync();
        await _storage.SaveSeenIdsAsync(AlertPolicy.AlertIds(_allItems, mode));
    }

    private async Task SaveSettingsAsync()
    {
        await _settingsWriteGate.WaitAsync();
        try
        {
            await _storage.SaveSettingsAsync(_settings);
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException)
        {
            Status = "Display preference could not be saved";
        }
        finally
        {
            _settingsWriteGate.Release();
        }
    }

    private async Task LoadImagesAsync(IReadOnlyList<FeedCardViewModel> cards, CancellationToken cancellationToken)
    {
        try
        {
            await Task.WhenAll(cards.Select(async card =>
            {
                var image = await _imageService.LoadAsync(card.Item.ImageUrl, cancellationToken);
                if (!cancellationToken.IsCancellationRequested) card.Image = image;
            }));
        }
        catch (OperationCanceledException)
        {
            // A new filter superseded this image batch.
        }
    }

    public void Dispose()
    {
        _imageCancellation?.Cancel();
        _imageCancellation?.Dispose();
        _feedService.Dispose();
        _imageService.Dispose();
    }
}

public sealed record AlertChoice(AlertMode Mode, string Label);
