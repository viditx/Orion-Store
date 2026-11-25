using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media.Animation;
using System.Windows.Navigation;
using Microsoft.Win32;

namespace OrionInstaller
{
    public partial class MainWindow : Window
    {
        private string targetDirectory;

        public MainWindow()
        {
            InitializeComponent();
            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            targetDirectory = Path.Combine(localAppData, "Programs", "OrionStore");
            InstallPathText.Text = targetDirectory;

            // Handle silent install if invoked via command-line
            string[] args = Environment.GetCommandLineArgs();
            foreach (var arg in args)
            {
                if (arg.Equals("/S", StringComparison.OrdinalIgnoreCase) ||
                    arg.Equals("/silent", StringComparison.OrdinalIgnoreCase) ||
                    arg.Equals("/quiet", StringComparison.OrdinalIgnoreCase))
                {
                    PerformSilentInstall();
                    break;
                }
            }
        }

        private void Window_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.ChangedButton == MouseButton.Left)
                DragMove();
        }

        private void MinButton_Click(object sender, RoutedEventArgs e)
        {
            WindowState = WindowState.Minimized;
        }

        private void CloseButton_Click(object sender, RoutedEventArgs e)
        {
            Close();
        }

        private void BrowseButton_Click(object sender, RoutedEventArgs e)
        {
            var dialog = new OpenFolderDialog
            {
                Title = "Select Orion Store Install Directory",
                InitialDirectory = targetDirectory
            };

            if (dialog.ShowDialog() == true)
            {
                targetDirectory = dialog.FolderName;
                if (!targetDirectory.EndsWith("OrionStore", StringComparison.OrdinalIgnoreCase))
                {
                    targetDirectory = Path.Combine(targetDirectory, "OrionStore");
                }
                InstallPathText.Text = targetDirectory;
            }
        }

        private void UpdateProgressUI(int percent, string? status, string? detail)
        {
            Dispatcher.Invoke(() =>
            {
                percent = Math.Clamp(percent, 0, 100);
                ProgressPercentText.Text = $"{percent}%";

                double trackWidth = ProgressTrack.ActualWidth > 0 ? ProgressTrack.ActualWidth : 428;
                ProgressBarFill.Width = (percent / 100.0) * trackWidth;

                if (!string.IsNullOrEmpty(status)) ProgressStatusText.Text = status;
                if (!string.IsNullOrEmpty(detail)) ProgressDetailText.Text = detail;
            });
        }

        private async void InstallButton_Click(object sender, RoutedEventArgs e)
        {
            ReadyView.Visibility = Visibility.Collapsed;
            InstallingView.Visibility = Visibility.Visible;
            InstallingView.UpdateLayout();

            bool makeDesktop = DesktopShortcutCheck.IsChecked == true;
            bool makeStartMenu = StartMenuCheck.IsChecked == true;

            try
            {
                await Task.Run(() => PerformInstallation(targetDirectory, makeDesktop, makeStartMenu, (percent, status, detail) =>
                {
                    UpdateProgressUI(percent, status, detail);
                }));

                InstallingView.Visibility = Visibility.Collapsed;
                CompletedView.Visibility = Visibility.Visible;
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Installation failed:\n{ex.Message}", "Orion Setup Error", MessageBoxButton.OK, MessageBoxImage.Error);
                InstallingView.Visibility = Visibility.Collapsed;
                ReadyView.Visibility = Visibility.Visible;
            }
        }

        /// <summary>Opens "Made by" links in the user's browser.</summary>
        private void Link_RequestNavigate(object sender, RequestNavigateEventArgs e)
        {
            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = e.Uri.AbsoluteUri,
                    UseShellExecute = true
                });
                e.Handled = true;
            }
            catch
            {
                // A link that will not open is not worth interrupting the install for.
            }
        }

        private void LaunchButton_Click(object sender, RoutedEventArgs e)
        {
            string exePath = Path.Combine(targetDirectory, "OrionStore.exe");
            if (File.Exists(exePath))
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = exePath,
                    WorkingDirectory = targetDirectory,
                    UseShellExecute = true
                });
            }
            Application.Current.Shutdown();
        }

        private void PerformSilentInstall()
        {
            try
            {
                PerformInstallation(targetDirectory, true, true, (_, _, _) => { });
                Environment.Exit(0);
            }
            catch
            {
                Environment.Exit(1);
            }
        }

        private static void PerformInstallation(string destination, bool createDesktop, bool createStartMenu, Action<int, string, string> progress)
        {
            Directory.CreateDirectory(destination);

            progress(5, "Reading installation payload...", "Locating bundled runtime and core packages");

            var assembly = Assembly.GetExecutingAssembly();
            using var stream = assembly.GetManifestResourceStream("OrionInstaller.payload.zip")
                ?? throw new InvalidOperationException("Embedded installation payload not found.");

            using var archive = new ZipArchive(stream, ZipArchiveMode.Read);
            long totalUncompressed = 0;
            foreach (var entry in archive.Entries)
            {
                totalUncompressed += entry.Length;
            }
            if (totalUncompressed <= 0) totalUncompressed = 1;

            long writtenBytes = 0;
            byte[] buffer = new byte[64 * 1024];
            int lastReportedPercent = -1;

            progress(8, "Extracting Orion Store Windows...", "Unpacking components");

            foreach (var entry in archive.Entries)
            {
                string targetPath = Path.Combine(destination, entry.FullName);

                if (string.IsNullOrEmpty(entry.Name))
                {
                    Directory.CreateDirectory(targetPath);
                    continue;
                }

                string? parentDir = Path.GetDirectoryName(targetPath);
                if (!string.IsNullOrEmpty(parentDir))
                {
                    Directory.CreateDirectory(parentDir);
                }

                using var entryStream = entry.Open();
                using var outStream = File.Create(targetPath);

                int read;
                while ((read = entryStream.Read(buffer, 0, buffer.Length)) > 0)
                {
                    outStream.Write(buffer, 0, read);
                    writtenBytes += read;

                    int percent = (int)(8 + ((double)writtenBytes / totalUncompressed) * 85);
                    percent = Math.Min(93, Math.Max(8, percent));
                    if (percent != lastReportedPercent)
                    {
                        lastReportedPercent = percent;
                        progress(percent, "Extracting Orion Store Windows...", entry.Name);
                    }
                }
            }

            progress(95, "Creating Windows Shortcuts...", "Desktop & Start Menu");
            Thread.Sleep(120);

            string exeFile = Path.Combine(destination, "OrionStore.exe");

            if (createDesktop)
            {
                CreateShortcut(
                    shortcutPath: Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "Orion Store Windows.lnk"),
                    targetPath: exeFile,
                    workingDirectory: destination,
                    description: "Orion Store Windows",
                    iconLocation: $"{exeFile},0"
                );
            }

            if (createStartMenu)
            {
                string startMenuDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.StartMenu), "Programs", "Orion Store Windows");
                Directory.CreateDirectory(startMenuDir);
                CreateShortcut(
                    shortcutPath: Path.Combine(startMenuDir, "Orion Store Windows.lnk"),
                    targetPath: exeFile,
                    workingDirectory: destination,
                    description: "Orion Store Windows",
                    iconLocation: $"{exeFile},0"
                );
            }
            Thread.Sleep(120);

            progress(97, "Registering Orion Desktop...", "Setting up Windows Apps registration");
            RegisterUninstaller(destination, exeFile);
            Thread.Sleep(150);

            // Reached 99% - pause visibly so the user sees 99%
            progress(99, "Finalizing installation...", "Almost ready");
            Thread.Sleep(700);

            // Smooth transition to 100%
            progress(100, "Setup Complete!", "Ready to launch");
            Thread.Sleep(800);
        }

        private static void CreateShortcut(string shortcutPath, string targetPath, string workingDirectory, string description, string iconLocation)
        {
            try
            {
                Type? shellType = Type.GetTypeFromProgID("WScript.Shell");
                if (shellType != null)
                {
                    object? shell = Activator.CreateInstance(shellType);
                    if (shell != null)
                    {
                        object? shortcut = shellType.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell, new object[] { shortcutPath });
                        if (shortcut != null)
                        {
                            Type scType = shortcut.GetType();
                            scType.InvokeMember("TargetPath", BindingFlags.SetProperty, null, shortcut, new object[] { targetPath });
                            scType.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, shortcut, new object[] { workingDirectory });
                            scType.InvokeMember("Description", BindingFlags.SetProperty, null, shortcut, new object[] { description });
                            scType.InvokeMember("IconLocation", BindingFlags.SetProperty, null, shortcut, new object[] { iconLocation });
                            scType.InvokeMember("Save", BindingFlags.InvokeMethod, null, shortcut, null);
                            return;
                        }
                    }
                }
            }
            catch { }

            try
            {
                string script = $@"
$ws = New-Object -ComObject WScript.Shell
$s = $ws.CreateShortcut('{shortcutPath.Replace("'", "''")}')
$s.TargetPath = '{targetPath.Replace("'", "''")}'
$s.WorkingDirectory = '{workingDirectory.Replace("'", "''")}'
$s.Description = '{description.Replace("'", "''")}'
$s.IconLocation = '{iconLocation.Replace("'", "''")}'
$s.Save()
";
                var psi = new ProcessStartInfo
                {
                    FileName = "powershell.exe",
                    Arguments = $"-NoProfile -NonInteractive -ExecutionPolicy Bypass -Command \"{script}\"",
                    CreateNoWindow = true,
                    UseShellExecute = false
                };
                using var proc = Process.Start(psi);
                proc?.WaitForExit(3000);
            }
            catch { }
        }

        private static void RegisterUninstaller(string installDir, string exeFile)
        {
            try
            {
                string uninstallBat = Path.Combine(installDir, "uninstall.bat");
                string batContent = $@"@echo off
taskkill /f /im OrionStore.exe >nul 2>&1
timeout /t 1 >nul
del ""%USERPROFILE%\Desktop\Orion Store.lnk"" >nul 2>&1
del ""%APPDATA%\Microsoft\Windows\Start Menu\Programs\Orion Store\Orion Store.lnk"" >nul 2>&1
rmdir /s /q ""%APPDATA%\Microsoft\Windows\Start Menu\Programs\Orion Store"" >nul 2>&1
reg delete ""HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\OrionStore"" /f >nul 2>&1
cd /d ""%LOCALAPPDATA%\Programs""
rmdir /s /q ""{installDir}"" >nul 2>&1
exit
";
                File.WriteAllText(uninstallBat, batContent);

                using var key = Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\OrionStore");
                if (key != null)
                {
                    key.SetValue("DisplayName", "Orion Store 2.0");
                    key.SetValue("DisplayVersion", "2.0.0");
                    key.SetValue("Publisher", "RookieEnough");
                    key.SetValue("DisplayIcon", $"{exeFile},0");
                    key.SetValue("InstallLocation", installDir);
                    key.SetValue("UninstallString", $"\"{uninstallBat}\"");
                    key.SetValue("URLInfoAbout", "https://orionstore.pages.dev");
                    key.SetValue("EstimatedSize", 169000, RegistryValueKind.DWord);
                    key.SetValue("NoModify", 1, RegistryValueKind.DWord);
                    key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
                }
            }
            catch { }
        }
    }
}
