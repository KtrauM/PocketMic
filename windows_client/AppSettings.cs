using System.Text.Json;
using System.IO;

namespace PocketMic.Windows
{
    public class AppSettings
    {
        public string LastIpAddress { get; set; } = "192.168.1.100";
        public string LastPort { get; set; } = "50005";
        public int LastOutputDeviceId { get; set; }

        private static readonly string SettingsPath = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "PocketMic",
            "settings.json");

        public static AppSettings Load()
        {
            try
            {
                if (File.Exists(SettingsPath))
                {
                    string json = File.ReadAllText(SettingsPath);
                    return JsonSerializer.Deserialize<AppSettings>(json) ?? new AppSettings();
                }
            }
            catch (Exception)
            {
                // If there's any error loading settings, return default settings
            }
            return new AppSettings();
        }

        public void Save()
        {
            try
            {
                string directory = Path.GetDirectoryName(SettingsPath)!;
                if (!Directory.Exists(directory))
                {
                    Directory.CreateDirectory(directory);
                }

                string json = JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true });
                File.WriteAllText(SettingsPath, json);
            }
            catch (Exception)
            {
                // Handle save error if needed
            }
        }
    }
} 