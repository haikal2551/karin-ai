[Setup]
AppName=KARIN AI Desktop
AppVersion=1.0.0
DefaultDirName={localappdata}\KARIN AI Desktop
DefaultGroupName=KARIN AI Desktop
OutputBaseFilename=Setup_KARIN_AI_Desktop
OutputDir=output
Compression=lzma2
SolidCompression=yes
PrivilegesRequired=lowest
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayName=KARIN AI Desktop

[Files]
Source: "..\publish\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion

[Icons]
Name: "{autodesktop}\KARIN AI Desktop"; Filename: "{app}\KARIN.AI.Desktop.exe"
Name: "{group}\KARIN AI Desktop"; Filename: "{app}\KARIN.AI.Desktop.exe"

[Run]
Filename: "{app}\KARIN.AI.Desktop.exe"; Description: "Launch KARIN AI Desktop"; Flags: nowait postinstall skipifsilent