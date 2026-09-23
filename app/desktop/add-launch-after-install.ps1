# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

# Adds a ticked "Launch Campfire" checkbox to the last page of an installer jpackage built, and has its Finish button
# start the app when the box is left ticked. jpackage's WiX sources have no such option, and the Compose plugin gives
# no way to hand jpackage sources of its own, so the finished database is edited instead - through the Windows
# Installer automation interface, which every Windows machine has. WiX's ExitDialog already carries the checkbox,
# hidden until the property holding its text is set, so all that is added is that property, the custom action and the
# event on Finish. Run once per .msi, and again over the same file without adding anything twice.
param(
    [Parameter(Mandatory = $true)][string] $Path,
    [Parameter(Mandatory = $true)][string] $Launcher,
    [Parameter(Mandatory = $true)][string] $Text
)
$ErrorActionPreference = 'Stop'

# The Windows Installer objects have no type library PowerShell can bind methods through, so every call is made by
# name.
function Invoke-Method($target, [string] $name, [object[]] $arguments) {
    $target.GetType().InvokeMember($name, 'InvokeMethod', $null, $target, $arguments)
}

function Get-ComProperty($target, [string] $name, [object[]] $arguments) {
    $target.GetType().InvokeMember($name, 'GetProperty', $null, $target, $arguments)
}

function Invoke-Query($database, [string] $sql) {
    $view = Invoke-Method $database 'OpenView' @($sql)
    Invoke-Method $view 'Execute' $null | Out-Null
    $rows = @()
    while ($record = Invoke-Method $view 'Fetch' $null) {
        $fields = [int](Get-ComProperty $record 'FieldCount' $null)
        $rows += , @(1..$fields | ForEach-Object { Get-ComProperty $record 'StringData' @([int]$_) })
    }
    Invoke-Method $view 'Close' $null | Out-Null
    , $rows
}

function Invoke-Statement($database, [string] $sql) {
    $view = Invoke-Method $database 'OpenView' @($sql)
    Invoke-Method $view 'Execute' $null | Out-Null
    Invoke-Method $view 'Close' $null | Out-Null
}

$installer = New-Object -ComObject WindowsInstaller.Installer
$database = Invoke-Method $installer 'OpenDatabase' @($Path, 1)

$checkbox = Invoke-Query $database "SELECT Control FROM Control WHERE Dialog_='ExitDialog' AND Control='OptionalCheckBox'"
if ($checkbox.Count -ne 1) { throw "$Path has no ExitDialog with an optional checkbox to show." }
# A file's name is either the long name alone or "SHORT~1.EXE|Long name.exe".
$files = Invoke-Query $database 'SELECT File, FileName FROM File'
$launchers = @($files.Where({ $_[1] -eq $Launcher -or $_[1].EndsWith("|$Launcher") }))
if ($launchers.Count -ne 1) { throw "Expected one $Launcher in $Path, found $($launchers.Count)." }
$launcherFile = $launchers[0][0]

Invoke-Statement $database "DELETE FROM ControlEvent WHERE Dialog_='ExitDialog' AND Control_='Finish' AND Event='DoAction' AND Argument='LaunchApplication'"
Invoke-Statement $database "DELETE FROM CustomAction WHERE Action='LaunchApplication'"
Invoke-Statement $database "DELETE FROM Property WHERE Property='WIXUI_EXITDIALOGOPTIONALCHECKBOX' OR Property='WIXUI_EXITDIALOGOPTIONALCHECKBOXTEXT'"

# 210 is an executable from the File table (18), started without waiting for it (128) and without failing the
# installation over its exit code (64). It runs from the Finish button rather than from the execute sequence, so a
# silent installation - the kind a store or an administrator runs - starts nothing.
Invoke-Statement $database "INSERT INTO CustomAction (Action, Type, Source) VALUES ('LaunchApplication', 210, '$launcherFile')"
Invoke-Statement $database "INSERT INTO Property (Property, Value) VALUES ('WIXUI_EXITDIALOGOPTIONALCHECKBOXTEXT', '$Text')"
Invoke-Statement $database "INSERT INTO Property (Property, Value) VALUES ('WIXUI_EXITDIALOGOPTIONALCHECKBOX', '1')"
# Before the EndDialog event the dialog already has on Finish, at 999. The box is only shown when the product was not
# installed before this run, which is a first installation or an upgrade, and not a repair or a removal.
Invoke-Statement $database "INSERT INTO ControlEvent (Dialog_, Control_, Event, Argument, Condition, Ordering) VALUES ('ExitDialog', 'Finish', 'DoAction', 'LaunchApplication', 'WIXUI_EXITDIALOGOPTIONALCHECKBOX = 1 AND NOT Installed', 1)"

Invoke-Method $database 'Commit' $null | Out-Null
[System.Runtime.InteropServices.Marshal]::ReleaseComObject($database) | Out-Null
