using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading.Tasks;
using System.Windows;
using NAudio.Wave;
using System.Windows.Controls;

namespace PocketMic.Windows
{
    public partial class MainWindow : Window
    {
        private TcpClient? _tcpClient;
        private WaveOutEvent? _waveOut;
        private BufferedWaveProvider? _waveProvider;
        private bool _isConnected;
        private Task? _receiveTask;
        private int _totalBytesReceived;
        private DateTime _lastLogTime = DateTime.Now;
        private readonly AppSettings _settings;
        private bool _isLoadingSettings;

        public MainWindow()
        {
            InitializeComponent();
            
            // Load saved settings
            _settings = AppSettings.Load();
            LoadAudioDevices();
            LoadSavedSettings();

            // Add event handlers for settings changes
            IpAddressTextBox.TextChanged += Settings_Changed;
            PortTextBox.TextChanged += Settings_Changed;
            OutputDeviceComboBox.SelectionChanged += Settings_Changed;
        }

        private void Settings_Changed(object sender, EventArgs e)
        {
            if (!_isLoadingSettings)
            {
                SaveSettings();
            }
        }

        private void LoadSavedSettings()
        {
            try
            {
                _isLoadingSettings = true;

                if (!string.IsNullOrEmpty(_settings.LastIpAddress))
                    IpAddressTextBox.Text = _settings.LastIpAddress;
                if (!string.IsNullOrEmpty(_settings.LastPort))
                    PortTextBox.Text = _settings.LastPort;
                
                // Find and select the saved device
                for (int i = 0; i < OutputDeviceComboBox.Items.Count; i++)
                {
                    var device = (DeviceInfo)OutputDeviceComboBox.Items[i];
                    if (device.Id == _settings.LastOutputDeviceId)
                    {
                        OutputDeviceComboBox.SelectedIndex = i;
                        break;
                    }
                }
            }
            catch (Exception ex)
            {
                LogMessage($"Error loading saved settings: {ex.Message}");
            }
            finally
            {
                _isLoadingSettings = false;
            }
        }

        private void SaveSettings()
        {
            try
            {
                _settings.LastIpAddress = IpAddressTextBox.Text;
                _settings.LastPort = PortTextBox.Text;

                var selectedDevice = OutputDeviceComboBox.SelectedItem as DeviceInfo;
                if (selectedDevice != null)
                {
                    _settings.LastOutputDeviceId = selectedDevice.Id;
                }

                _settings.Save();
            }
            catch (Exception ex)
            {
                LogMessage($"Error saving settings: {ex.Message}");
            }
        }

        private void LoadAudioDevices()
        {
            OutputDeviceComboBox.Items.Clear();
            for (int i = 0; i < WaveOut.DeviceCount; i++)
            {
                var capabilities = WaveOut.GetCapabilities(i);
                OutputDeviceComboBox.Items.Add(new DeviceInfo { Id = i, Name = capabilities.ProductName });
            }
            if (OutputDeviceComboBox.Items.Count > 0)
            {
                OutputDeviceComboBox.DisplayMemberPath = "Name";
                OutputDeviceComboBox.SelectedIndex = 0;
            }
        }

        private async void ConnectButton_Click(object sender, RoutedEventArgs e)
        {
            if (_isConnected) return;

            try
            {
                LogMessage($"Attempting to connect to {IpAddressTextBox.Text}:{PortTextBox.Text}");
                _tcpClient = new TcpClient();
                await _tcpClient.ConnectAsync(IpAddressTextBox.Text, int.Parse(PortTextBox.Text));
                
                // Save settings after successful connection
                SaveSettings();
                
                _isConnected = true;
                _totalBytesReceived = 0;
                _lastLogTime = DateTime.Now;
                UpdateConnectionState(true);
                LogMessage("TCP connection established successfully");

                // Initialize audio playback
                InitializeAudioPlayback();

                // Start receiving audio data
                _receiveTask = Task.Run(ReceiveAudioData);
            }
            catch (Exception ex)
            {
                LogMessage($"Connection failed: {ex.Message}");
                Disconnect();
            }
        }

        private void DisconnectButton_Click(object sender, RoutedEventArgs e)
        {
            Disconnect();
        }

        private void Disconnect()
        {
            _isConnected = false;
            _tcpClient?.Close();
            _tcpClient = null;
            _waveOut?.Stop();
            _waveOut?.Dispose();
            _waveOut = null;
            UpdateConnectionState(false);
            LogMessage("Disconnected");
        }

        private void UpdateConnectionState(bool connected)
        {
            ConnectButton.IsEnabled = !connected;
            DisconnectButton.IsEnabled = connected;
            StatusTextBlock.Text = connected ? "Connected" : "Not Connected";
        }

        private void InitializeAudioPlayback()
        {
            try
            {
                var selectedDevice = (DeviceInfo)OutputDeviceComboBox.SelectedItem;
                LogMessage($"Initializing audio playback on device: {selectedDevice.Name} (ID: {selectedDevice.Id})");

                _waveOut = new WaveOutEvent();
                _waveOut.DeviceNumber = selectedDevice.Id;
                _waveProvider = new BufferedWaveProvider(new WaveFormat(44100, 16, 1));
                _waveProvider.DiscardOnBufferOverflow = true;
                _waveOut.Init(_waveProvider);
                _waveOut.Play();
                LogMessage("Audio playback initialized successfully");
            }
            catch (Exception ex)
            {
                LogMessage($"Error initializing audio playback: {ex.Message}");
                throw;
            }
        }

        private async Task ReceiveAudioData()
        {
            if (_tcpClient == null) return;

            var buffer = new byte[4096];
            var stream = _tcpClient.GetStream();

            try
            {
                LogMessage("Starting to receive audio data...");
                while (_isConnected)
                {
                    try
                    {
                        int bytesRead = await stream.ReadAsync(buffer, 0, buffer.Length);
                        if (bytesRead == 0)
                        {
                            LogMessage("Connection closed by server (received 0 bytes)");
                            break;
                        }

                        _totalBytesReceived += bytesRead;
                        
                        // Log audio statistics every 2 seconds
                        var now = DateTime.Now;
                        if ((now - _lastLogTime).TotalSeconds >= 2)
                        {
                            var bytesPerSecond = _totalBytesReceived / (now - _lastLogTime).TotalSeconds;
                            var bufferSize = _waveProvider?.BufferedBytes ?? 0;
                            LogMessage($"Audio stats - Received: {bytesPerSecond:F0} bytes/sec, Buffer: {bufferSize} bytes");
                            _totalBytesReceived = 0;
                            _lastLogTime = now;
                        }

                        if (_waveProvider != null)
                        {
                            try
                            {
                                _waveProvider.AddSamples(buffer, 0, bytesRead);
                            }
                            catch (Exception ex)
                            {
                                LogMessage($"Error adding samples to buffer: {ex.Message}");
                            }
                        }
                    }
                    catch (Exception ex)
                    {
                        LogMessage($"Error reading from stream: {ex.Message}");
                        break;
                    }
                }
            }
            catch (Exception ex)
            {
                LogMessage($"Error in audio reception loop: {ex.Message}");
            }
            finally
            {
                Dispatcher.Invoke(Disconnect);
            }
        }

        private void LogMessage(string message)
        {
            Dispatcher.Invoke(() =>
            {
                LogTextBox.AppendText($"[{DateTime.Now:HH:mm:ss}] {message}{Environment.NewLine}");
                LogTextBox.ScrollToEnd();
            });
        }
    }

    public class DeviceInfo
    {
        public int Id { get; set; }
        public string Name { get; set; } = string.Empty;
    }
} 