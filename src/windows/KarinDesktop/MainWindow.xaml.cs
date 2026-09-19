using System.Diagnostics;
using System.Speech.Recognition;
using System.Speech.Synthesis;
using System.Windows;
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
    private bool _talkState;

    public MainWindow()
    {
        InitializeComponent();
        _voice.Rate = 1;
        _timer.Interval = TimeSpan.FromSeconds(1);
        _timer.Tick += (_, _) => UpdateSystem();
        _timer.Start();
        HistoryBox.AppendText("KARIN AI Desktop started.\r\n\r\n");
    }

    private void Speak(string text)
    {
        StatusText.Text = text;
        HistoryBox.AppendText($"KARIN: {text}\r\n\r\n");
        AnimateSpeak(true);
        try
        {
            _voice.SpeakAsyncCancelAll();
            _voice.SpeakCompleted += Voice_SpeakCompleted;
            _voice.SpeakAsync(text);
        }
        catch { AnimateSpeak(false); }
    }

    private void Voice_SpeakCompleted(object? sender, SpeakCompletedEventArgs e)
    {
        Dispatcher.Invoke(() => AnimateSpeak(false));
        _voice.SpeakCompleted -= Voice_SpeakCompleted;
    }

    private void AnimateSpeak(bool on)
    {
        _talkState = on;
        Mouth.Height = on ? 23 : 5;
        Mouth.Width = on ? 30 : 42;
        AvatarCore.BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString(on ? "#E5FCFF" : "#BFEFFF"));
        StatusText.Text = on ? "KARIN IS SPEAKING" : "READY • ASK KARIN";
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
        MemText.Text = $"KARIN MANAGED MEMORY  {pct:0}%";
    }

    private void Route(string raw)
    {
        var text = raw.Trim();
        if (text.Length == 0) return;
        HistoryBox.AppendText($"YOU: {text}\r\n");
        var t = text.ToLowerInvariant();

        if (t.Contains("buka dokumen"))
        {
            OpenPath(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments));
            Speak("Folder Dokumen dibuka.");
        }
        else if (t.Contains("buka download"))
        {
            OpenPath(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads"));
            Speak("Folder Downloads dibuka.");
        }
        else if (t.Contains("buka browser"))
        {
            Process.Start(new ProcessStartInfo("https://www.google.com") { UseShellExecute = true });
            Speak("Browser dibuka.");
        }
        else if (t.StartsWith("cari file "))
        {
            SearchFile(text["cari file ".Length..]);
        }
        else if (t.Contains("status sistem"))
        {
            Speak($"KARIN aktif. Proses ini menggunakan sekitar {Math.Round(_self.WorkingSet64 / 1024d / 1024d)} megabyte memori.");
        }
        else if (t.Contains("istirahat"))
        {
            MessageBox.Show("Haikal, sudah waktunya istirahat sejenak dari komputer.", "KARIN Routine", MessageBoxButton.OK, MessageBoxImage.Information);
            Speak("Sudah waktunya istirahat sejenak.");
        }
        else if (t.Contains("android") || t.Contains("pair"))
        {
            _ = StartBridge();
        }
        else
        {
            Speak("Perintah diterima. Versi ini mendukung launcher, dokumen, reminder, voice, system status, dan KARIN Link.");
        }
    }

    private static void OpenPath(string path)
    {
        if (Directory.Exists(path))
            Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
    }

    private void SearchFile(string query)
    {
        var root = Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments);
        string? found = null;
        try
        {
            found = Directory.EnumerateFiles(root, "*", SearchOption.AllDirectories)
                .FirstOrDefault(f => Path.GetFileName(f).Contains(query, StringComparison.OrdinalIgnoreCase));
        }
        catch { }

        if (found is null) { Speak("Aku belum menemukan file itu di Dokumen."); return; }
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

        try
        {
            await _bridge.StartAsync();
            BridgeBadge.Text = "ANDROID: BRIDGE ON";
            PairInfo.Text = $"Port {_bridge.Port} • Token {_bridge.Token}\nShared: Downloads";
            Speak($"KARIN Link aktif. Token pairing {_bridge.Token}.");
        }
        catch (Exception ex)
        {
            MessageBox.Show("KARIN Link gagal dibuka. Windows Firewall mungkin meminta izin.\n\n" + ex.Message, "KARIN Link");
        }
    }

    private void Mic_Click(object sender, RoutedEventArgs e)
    {
        if (_wakeOn)
        {
            try { _recognizer?.RecognizeAsyncStop(); } catch { }
            _wakeOn = false;
            WakeBadge.Text = "WAKE WORD: OFF";
            MicButton.Content = "🎙 Activate “Karin”";
            Speak("Wake word dimatikan.");
            return;
        }

        try
        {
            _recognizer = new SpeechRecognitionEngine();
            var choices = new Choices("Karin", "karin");
            _recognizer.LoadGrammar(new Grammar(new GrammarBuilder(choices)));
            _recognizer.SetInputToDefaultAudioDevice();
            _recognizer.SpeechRecognized += (_, a) =>
            {
                if (a.Result.Confidence >= 0.5)
                    Dispatcher.Invoke(() => Speak("Iya, aku di sini."));
            };
            _recognizer.RecognizeAsync(RecognizeMode.Multiple);
            _wakeOn = true;
            WakeBadge.Text = "WAKE WORD: LISTENING";
            MicButton.Content = "🎙 Wake Word ON";
            Speak("Wake word Karin aktif.");
        }
        catch (Exception ex)
        {
            MessageBox.Show("Wake word belum bisa aktif. Pastikan mikrofon dan Windows Speech Recognition tersedia.\n\n" + ex.Message, "KARIN Voice");
        }
    }

    private void Send_Click(object s, RoutedEventArgs e) { Route(CommandBox.Text); CommandBox.Clear(); }
    private void CommandBox_KeyDown(object s, KeyEventArgs e) { if (e.Key == Key.Enter) { Route(CommandBox.Text); CommandBox.Clear(); } }
    private void Clear_Click(object s, RoutedEventArgs e) => HistoryBox.Clear();
    private void Core_Click(object s, RoutedEventArgs e) => Speak("KARIN Core siap.");
    private void SpeakDemo_Click(object s, RoutedEventArgs e) => Speak("Halo Haikal. Karin AI Desktop aktif dan siap membantu.");
    private void Documents_Click(object s, RoutedEventArgs e) => Route("buka dokumen");
    private void SystemStatus_Click(object s, RoutedEventArgs e) => Route("status sistem");
    private async void Android_Click(object s, RoutedEventArgs e) => await StartBridge();
    private async void StartBridge_Click(object s, RoutedEventArgs e) => await StartBridge();
    private void Rest_Click(object s, RoutedEventArgs e) => Route("istirahat");
    private void Apps_Click(object s, RoutedEventArgs e) => Process.Start(new ProcessStartInfo("explorer.exe", "shell:AppsFolder") { UseShellExecute = true });
    private void Routine_Click(object s, RoutedEventArgs e) => MessageBox.Show("08:00 Start work\n12:00 Lunch\n15:30 Rest\n22:30 Stop computer\n23:00 Sleep\n06:30 Wake", "KARIN Routine");
    private void Settings_Click(object s, RoutedEventArgs e) => MessageBox.Show("Ice Blue UI • Voice • Local document access • Android bridge", "KARIN Settings");

    protected override void OnClosed(EventArgs e)
    {
        try { _recognizer?.RecognizeAsyncCancel(); _recognizer?.Dispose(); } catch { }
        try { _voice.Dispose(); } catch { }
        try { _bridge?.Dispose(); } catch { }
        base.OnClosed(e);
    }
}