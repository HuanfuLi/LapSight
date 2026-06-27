$ErrorActionPreference = "Stop"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

& $adb shell pm clear com.huanfuli.lapsight
& $adb shell monkey -p com.huanfuli.lapsight -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 3

function Tap-Button {
    param([string]$Text)
    & $adb shell uiautomator dump /sdcard/window_dump.xml | Out-Null
    & $adb pull /sdcard/window_dump.xml ./window_dump.xml | Out-Null
    
    $xml = [xml](Get-Content ./window_dump.xml)
    $node = $xml.SelectNodes("//node[@text='$Text']")
    if ($node -eq $null -or $node.Count -eq 0) {
        Write-Host "Button '$Text' not found!"
        return $false
    }
    
    # Extract bounds [x1,y1][x2,y2]
    $bounds = $node[0].bounds
    if ($bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        $x1 = [int]$matches[1]
        $y1 = [int]$matches[2]
        $x2 = [int]$matches[3]
        $y2 = [int]$matches[4]
        
        $cx = [math]::Round(($x1 + $x2) / 2)
        $cy = [math]::Round(($y1 + $y2) / 2)
        
        Write-Host "Tapping '$Text' at $cx, $cy"
        & $adb shell input tap $cx $cy
        return $true
    }
    return $false
}

Tap-Button "Mark New Track"
Write-Host "Waiting 175 seconds to capture all 241 samples for 5 loops..."
Start-Sleep -Seconds 175
Tap-Button "Stop Marking"
Start-Sleep -Seconds 3

# Wait for review screen
& $adb shell uiautomator dump /sdcard/window_dump.xml | Out-Null
& $adb pull /sdcard/window_dump.xml ./window_dump_result.xml | Out-Null
Write-Host "Done!"
Tap-Button "Save Track"
Start-Sleep -Seconds 2
& $adb shell uiautomator dump /sdcard/window_dump_after_save.xml | Out-Null
& $adb pull /sdcard/window_dump_after_save.xml ./window_dump_after_save.xml | Out-Null
Tap-Button "Start Timing" 
Start-Sleep -Seconds 2
& $adb shell screencap -p /sdcard/timing1.png
Start-Sleep -Milliseconds 200
& $adb shell screencap -p /sdcard/timing2.png
Start-Sleep -Milliseconds 200
& $adb shell screencap -p /sdcard/timing3.png
& $adb pull /sdcard/timing1.png ./timing1.png | Out-Null
& $adb pull /sdcard/timing2.png ./timing2.png | Out-Null
& $adb pull /sdcard/timing3.png ./timing3.png | Out-Null
