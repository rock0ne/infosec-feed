namespace InfoSecFeed.Windows.Services;

public static class DisplayPreferences
{
    public const double MinimumTextScale = 0.9;
    public const double MaximumTextScale = 1.6;
    public const double DefaultTextScale = 1.15;
    public const double TextScaleStep = 0.1;

    public static double NormalizeTextScale(double value)
    {
        // Older settings files have no textScale property and deserialize it as
        // zero. Treat that as migration to the accessible default, not 90%.
        if (!double.IsFinite(value) || value < 0.5) return DefaultTextScale;
        return Math.Round(Math.Clamp(value, MinimumTextScale, MaximumTextScale), 2);
    }

    public static double Increase(double value) => NormalizeTextScale(value + TextScaleStep);

    public static double Decrease(double value) => NormalizeTextScale(value - TextScaleStep);

    public static string Percentage(double value) => $"{NormalizeTextScale(value):P0}";
}
