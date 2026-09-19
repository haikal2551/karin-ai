using System.Diagnostics;
using System.IO;
using System.Speech.Recognition;
using System.Speech.Synthesis;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;

namespace KarinDesktop;

public partial class MainWindow : Window
{
    private readonly SpeechSynthesizer _voice = new();
    private SpeechRecognitionEngine? _recognizer;
    private readonly DispatcherTimer _timer = new();
    private readonly Process _self = Process.GetCurrentProcess();
    private TimeSpan _lastCpu;
    private DateTime _lastCpuTime = DateTime.UtcNow;
    private AndroidBridge? _bridge;
    private bool _wakeOn;
    private bool _awaitingCommand;

    public MainWindow()
    {
        InitializeComponent();
        ConfigureVoice();

        _timer.Interval = TimeSpan.FromSeconds(1);
        _timer.Tick += (_, _) => UpdateSystem();
        _timer.Start();

        HistoryBox.AppendText("KARIN AI Desktop ready.\r\n");
        HistoryBox.AppendText("Say “Karin” or type a command below.\r\n\r\n");
    }

    private void ConfigureVoice()
    {
        _voice.Rate = 0;
        _voice.Volume = 100;

        try
        {
            var female = _voice.GetInstalledVoices()
                .Where(v => v.Enabled)
                .Select(v => v.VoiceInfo)
                .FirstOrDefault(v => v.Gender == VoiceGender.Female);

            if (female is not null)
            {
                _voice.SelectVoice(female.Name);
                VoiceNameText.Text = $"Female voice • {female.Name}";
                VoiceStateText.Text = $"Female voice active: {female.Name}";
            }
            else
            {
                VoiceNameText.Text = $"Windows voice • {_voice.Voice.Name}";
                VoiceStateText.Text = "Female voice not installed; using Windows default";
            }
        }
        catch
        {
            VoiceNameText.Text = "Windows default voice";
        }
    }

    private void Speak(string text)
    {
        Dispatcher.Invoke(() =>
        {
            HistoryBox.AppendText($"KARIN: {text}\r\n\r\n");
            StatusText.Text = "Karin is speaking…";
            ListeningHint.Text = "Speaking";
            AnimateSpeak(true);
        });

        try
        {
            _voice.SpeakAsyncCancelAll();
            _voice.SpeakCompleted -= Voice_SpeakCompleted;
            _voice.SpeakCompleted += Voice_SpeakCompleted;
            _voice.SpeakAsync(text);
        }
        catch
        {
            AnimateSpeak(false);
        }
    }

    private void Voice_SpeakCompleted(object? sender, SpeakCompletedEventArgs e)
    {
        Dispatcher.Invoke(() =>
        {
            AnimateSpeak(false);
            StatusText.Text = _wakeOn ? "Listening • say “Karin”" : "Ready • call me by saying “Karin”";
            ListeningHint.Text = _wakeOn ? "Wake word listening" : "Voice idle";
        });
    }

    private void AnimateSpeak(bool on)
    {
        Mouth.Height = on ? 20 : 4;
        Mouth.Width = on ? 25 : 34;
        AvatarCore.BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString(on ? "#F0FDFF" : "#BDEFFF"));
    }

    private void UpdateSystem()
    {
        ClockText.Text = DateTime.Now.ToString("HH:mm:ss");
        _self.Refresh();
        ProcText.Text = $"KARIN PROCESS  {Math.Round(_self.WorkingSet64 / 1024d / 1024d)} MB";

        var now = DateTime.UtcNow;
        var cpuNow = _self.TotalProcessorTime;
        var cpuDelta = (cpuNow - _lastCpu).TotalMilliseconds;
        var wall = (now - _lastCpuTime).TotalMilliseconds;
        var cpu = _lastCpu == TimeSpan.Zero || wall <= 0 ? 0 :
            Math.Clamp(cpuDelta / (Environment.ProcessorCount * wall) * 100, 0, 100);

        _lastCpu = cpuNow;
        _lastCpuTime = now;
        CpuBar.Value = cpu;
        CpuText.Text = $"KARIN CPU  {cpu:0.0}%";

        var gc = GC.GetGCMemoryInfo();
        var total = gc.TotalAvailableMemoryBytes;
        var used = GC.GetTotalMemory(false);
        var pct = total > 0 ? Math.Clamp(used * 100d / total, 0, 100) : 0;
        MemBar.Value = pct;
        MemText.Text = $"KARIN MEMORY  {pct:0}%";
    }

    private void Route(string raw)
    {
        var text = raw.Trim();
        if (text.Length == 0) return;

        HistoryBox.AppendText($"YOU: {text}\r\n");
        var t = text.ToLowerInvariant();

        if (t.StartsWith("karin "))
            t = t[6..].Trim();

        if (t is "mode bisnis" or "business mode" || t.Contains("masuk mode bisnis"))
        {
            ShowBusinessMode();
            Speak("Mode bisnis dibuka.");
        }
        else if (t is "mode karin" or "home" || t.Contains("kembali ke karin"))
        {
            ShowPersonalMode();
            Speak("Kembali ke Karin.");
        }
        else if (t.Contains("buka finance") || t == "finance")
        {
            ShowBusinessMode();
            Speak("Modul Finance siap. Integrasi transaksi penuh akan menggunakan sumber data bisnis yang kamu hubungkan.");
        }
        else if (t.Contains("buka dokumen"))
        {
            OpenPath(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments));
            Speak("Folder Dokumen dibuka.");
        }
        else if (t.Contains("buka download"))
        {
            OpenPath(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads"));
            Speak("Folder Downloads dibuka.");
        }
        else if (t.Contains("buka browser") || t.Contains("buka chrome"))
        {
            Process.Start(new ProcessStartInfo("https://www.google.com") { UseShellExecute = true });
            Speak("Browser dibuka.");
        }
        else if (t.StartsWith("cari file "))
        {
            SearchFile(text["cari file ".Length..]);
        }
        else if (t.Contains("buka aplikasi"))
        {
            Apps_Click(this, new RoutedEventArgs());
            Speak("Daftar aplikasi Windows dibuka.");
        }
        else if (t.Contains("status sistem") || t.Contains("cek sistem"))
        {
            Speak($"KARIN aktif. Proses menggunakan sekitar {Math.Round(_self.WorkingSet64 / 1024d / 1024d)} megabyte memori.");
        }
        else if (t.Contains("istirahat"))
        {
            MessageBox.Show("Haikal, sudah waktunya berhenti sejenak dan beristirahat.", "KARIN Routine",
                MessageBoxButton.OK, MessageBoxImage.Information);
            Speak("Sudah waktunya istirahat sejenak.");
        }
        else if (t.Contains("android") || t.Contains("pair"))
        {
            _ = StartBridge();
        }
        else if (t.Contains("jam berapa"))
        {
            Speak($"Sekarang pukul {DateTime.Now:HH mm}.");
        }
        else
        {
            Speak("Perintahnya sudah kudengar, tetapi aksi khusus untuk perintah itu belum dipasang. Kamu tetap bisa memakai launcher, dokumen, routine, mode bisnis, sistem, dan Android Link.");
        }
    }

    private static void OpenPath(string path)
    {
        if (Directory.Exists(path))
            Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
    }

    private void SearchFile(string query)
    {
        var roots = new[]
        {
            Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads"),
            Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory)
        };

        string? found = null;
        foreach (var root in roots)
        {
            try
            {
                found = Directory.EnumerateFiles(root, "*", SearchOption.AllDirectories)
                    .FirstOrDefault(f => Path.GetFileName(f).Contains(query, StringComparison.OrdinalIgnoreCase));
                if (found is not null) break;
            }
            catch { }
        }

        if (found is null)
        {
            Speak("Aku belum menemukan file itu di Dokumen, Downloads, atau Desktop.");
            return;
        }

        Process.Start(new ProcessStartInfo(found) { UseShellExecute = true });
        Speak($"File {Path.GetFileName(found)} ditemukan dan dibuka.");
    }

    private async Task StartBridge()
    {
        if (_bridge?.Running == true)
        {
            Speak($"KARIN Link sudah aktif. Token pairing {_bridge.Token}.");
            return;
        }

        var shared = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads");
        _bridge = new AndroidBridge(shared);
        _bridge.CommandReceived += command =>
            Dispatcher.Invoke(() => Route(command));

        try
        {
            await _bridge.StartAsync();
            BridgeBadge.Text = "ANDROID • BRIDGE ON";
            PairInfo.Text = $"Port {_bridge.Port}\nToken {_bridge.Token}\nShared: Downloads";
            Speak($"KARIN Link aktif. Token pairing {_bridge.Token}.");
        }
        catch (Exception ex)
        {
            MessageBox.Show("KARIN Link gagal dibuka. Windows Firewall atau URL permission mungkin perlu diizinkan.\n\n" + ex.Message, "KARIN Link");
        }
    }

    private void Mic_Click(object sender, RoutedEventArgs e)
    {
        if (_wakeOn)
        {
            StopRecognition();
            Speak("Voice core dimatikan.");
            return;
        }

        try
        {
            _recognizer = new SpeechRecognitionEngine();
            _recognizer.LoadGrammar(new DictationGrammar());
            _recognizer.SetInputToDefaultAudioDevice();
            _recognizer.SpeechRecognized += Recognizer_SpeechRecognized;
            _recognizer.RecognizeAsync(RecognizeMode.Multiple);

            _wakeOn = true;
            WakeBadge.Text = "VOICE • LISTENING";
            MicButton.Content = "🎙  Voice Core ON";
            ListeningHint.Text = "Listening for “Karin”";
            VoiceStateText.Text = "Wake word + command recognition active";
            Speak("Voice core aktif. Panggil aku dengan kata Karin.");
        }
        catch (Exception ex)
        {
            MessageBox.Show("Voice core belum bisa aktif. Pastikan mikrofon dan Windows Speech Recognition tersedia.\n\n" + ex.Message, "KARIN Voice");
        }
    }

    private void Recognizer_SpeechRecognized(object? sender, SpeechRecognizedEventArgs e)
    {
        if (e.Result.Confidence < 0.45) return;

        var heard = e.Result.Text.Trim();
        Dispatcher.Invoke(() => ListeningHint.Text = $"Heard: {heard}");

        if (_awaitingCommand)
        {
            _awaitingCommand = false;
            Dispatcher.Invoke(() => Route(heard));
            return;
        }

        var lower = heard.ToLowerInvariant();
        var idx = lower.IndexOf("karin", StringComparison.Ordinal);
        if (idx < 0) return;

        var afterWake = heard[(idx + 5)..].Trim(' ', ',', '.', ':', '-');
        if (afterWake.Length > 0)
        {
            Dispatcher.Invoke(() => Route(afterWake));
        }
        else
        {
            _awaitingCommand = true;
            Speak("Iya, aku dengar. Silakan ucapkan perintahmu.");
        }
    }

    private void StopRecognition()
    {
        try { _recognizer?.RecognizeAsyncCancel(); } catch { }
        try { _recognizer?.Dispose(); } catch { }
        _recognizer = null;
        _wakeOn = false;
        _awaitingCommand = false;
        WakeBadge.Text = "VOICE • OFF";
        MicButton.Content = "🎙  Activate wake word “Karin”";
        ListeningHint.Text = "Voice idle";
        VoiceStateText.Text = "Voice engine ready";
    }

    private void ShowBusinessMode()
    {
        PersonalModeGrid.Visibility = Visibility.Collapsed;
        BusinessModeGrid.Visibility = Visibility.Visible;
        ModeCaption.Text = "Business Workspace • Ice Blue";
        BusinessModeButton.Content = "◈   Business Mode Active";
    }

    private void ShowPersonalMode()
    {
        BusinessModeGrid.Visibility = Visibility.Collapsed;
        PersonalModeGrid.Visibility = Visibility.Visible;
        ModeCaption.Text = "Personal AI Desktop • Ice Blue";
        BusinessModeButton.Content = "◈   Enter Business Mode";
    }

    private void BusinessMode_Click(object s, RoutedEventArgs e) => ShowBusinessMode();
    private void PersonalMode_Click(object s, RoutedEventArgs e) => ShowPersonalMode();

    private void BusinessModule_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button b)
            Speak($"Modul {b.Content} dipilih.");
    }

    private void TopBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ButtonState == MouseButtonState.Pressed) DragMove();
    }

    private void Minimize_Click(object s, RoutedEventArgs e) => WindowState = WindowState.Minimized;
    private void Maximize_Click(object s, RoutedEventArgs e) => WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
    private void Close_Click(object s, RoutedEventArgs e) => Close();

    private void Send_Click(object s, RoutedEventArgs e)
    {
        Route(CommandBox.Text);
        CommandBox.Clear();
    }

    private void CommandBox_KeyDown(object s, KeyEventArgs e)
    {
        if (e.Key == Key.Enter)
        {
            Route(CommandBox.Text);
            CommandBox.Clear();
        }
    }

    private void Clear_Click(object s, RoutedEventArgs e) => HistoryBox.Clear();
    private void Core_Click(object s, RoutedEventArgs e) { ShowPersonalMode(); Speak("KARIN Core siap."); }
    private void Documents_Click(object s, RoutedEventArgs e) => Route("buka dokumen");
    private void SystemStatus_Click(object s, RoutedEventArgs e) => Route("status sistem");
    private async void Android_Click(object s, RoutedEventArgs e) => await StartBridge();
    private async void StartBridge_Click(object s, RoutedEventArgs e) => await StartBridge();

    private void Apps_Click(object s, RoutedEventArgs e)
    {
        Process.Start(new ProcessStartInfo("explorer.exe", "shell:AppsFolder") { UseShellExecute = true });
    }

    private void Routine_Click(object s, RoutedEventArgs e)
    {
        MessageBox.Show("08:00 Start work\n12:00 Lunch\n15:30 Rest\n22:30 Stop computer\n23:00 Sleep\n06:30 Wake up",
            "KARIN Routine", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void Settings_Click(object s, RoutedEventArgs e)
    {
        MessageBox.Show($"Ice Blue UI\nVoice: {_voice.Voice.Name}\nLocal document access\nAndroid bridge\nBusiness workspace",
            "KARIN Settings");
    }

    protected override void OnClosed(EventArgs e)
    {
        StopRecognition();
        try { _voice.Dispose(); } catch { }
        try { _bridge?.Dispose(); } catch { }
        base.OnClosed(e);
    }
}