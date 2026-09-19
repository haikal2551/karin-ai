using System.Net;
using System.Text;
using System.Text.Json;

namespace KarinDesktop;

public sealed class AndroidBridge : IDisposable
{
    private HttpListener? _listener;
    private CancellationTokenSource? _cts;
    private readonly string _token = Convert.ToHexString(Guid.NewGuid().ToByteArray())[..12];
    private readonly string _sharedFolder;

    public int Port { get; } = 51721;
    public string Token => _token;
    public bool Running => _listener?.IsListening == true;

    public AndroidBridge(string sharedFolder) => _sharedFolder = sharedFolder;

    public Task StartAsync()
    {
        if (Running) return Task.CompletedTask;
        _cts = new CancellationTokenSource();
        _listener = new HttpListener();
        _listener.Prefixes.Add($"http://+:{Port}/");
        _listener.Start();
        _ = Task.Run(() => LoopAsync(_cts.Token));
        return Task.CompletedTask;
    }

    private async Task LoopAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _listener?.IsListening == true)
        {
            HttpListenerContext ctx;
            try { ctx = await _listener.GetContextAsync(); }
            catch { break; }

            try
            {
                var path = ctx.Request.Url?.AbsolutePath ?? "/";
                if (path == "/ping")
                {
                    await Json(ctx, new { ok = true, device = Environment.MachineName, app = "KARIN AI Desktop" });
                    continue;
                }

                var supplied = ctx.Request.Headers["X-KARIN-TOKEN"] ?? ctx.Request.QueryString["token"];
                if (!string.Equals(supplied, _token, StringComparison.Ordinal))
                {
                    ctx.Response.StatusCode = 401;
                    ctx.Response.Close();
                    continue;
                }

                if (path == "/files")
                {
                    var files = Directory.Exists(_sharedFolder)
                        ? Directory.EnumerateFiles(_sharedFolder).Select(Path.GetFileName).Take(200).ToArray()
                        : Array.Empty<string?>();
                    await Json(ctx, new { ok = true, folder = _sharedFolder, files });
                    continue;
                }

                if (path == "/download")
                {
                    var name = Path.GetFileName(ctx.Request.QueryString["name"]);
                    var full = Path.Combine(_sharedFolder, name ?? "");
                    if (!File.Exists(full)) { ctx.Response.StatusCode = 404; ctx.Response.Close(); continue; }
                    ctx.Response.ContentType = "application/octet-stream";
                    ctx.Response.AddHeader("Content-Disposition", $"attachment; filename=\"{Path.GetFileName(full)}\"");
                    await using var fs = File.OpenRead(full);
                    await fs.CopyToAsync(ctx.Response.OutputStream, ct);
                    ctx.Response.Close();
                    continue;
                }

                ctx.Response.StatusCode = 404;
                ctx.Response.Close();
            }
            catch
            {
                try { ctx.Response.StatusCode = 500; ctx.Response.Close(); } catch { }
            }
        }
    }

    private static async Task Json(HttpListenerContext ctx, object obj)
    {
        var bytes = JsonSerializer.SerializeToUtf8Bytes(obj);
        ctx.Response.ContentType = "application/json";
        ctx.Response.ContentLength64 = bytes.Length;
        await ctx.Response.OutputStream.WriteAsync(bytes);
        ctx.Response.Close();
    }

    public void Dispose()
    {
        try { _cts?.Cancel(); } catch { }
        try { _listener?.Stop(); } catch { }
        try { _listener?.Close(); } catch { }
    }
}