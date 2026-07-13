# ALTablet
ALTablet turns your Android tablet into a high-performance graphic tablet for your computer, **focus on ultra-low latency** for competitive gaming. 

## ALTablet with [osu!](https://osu.ppy.sh/)

![](https://raw.githubusercontent.com/Jibhong/ALTablet/refs/heads/main/README/sample.webp)

# Features
|  |  Feature |
| --- | --- |
| ✅ | **Low Latency Input Stream**|
| ✅ | **High Polling Rate** (~480Hz on Samsung Galaxy Tab S8) |
| ✅ | Control your mouse with your Android device's stylus |
| ✅ | Customize tablet input area (Position, Scale and Aspect Ratio) |
| ✅ | Custom Background Image |
| ⏬ | Stylus pressure support |

✅ Finished
⏳ Someday...
⏬ Not Planned
🚫 Won't Do

# Installations
Grab two packages from the [release page](https://github.com/Jibhong/ALTablet/releases)
## Linux PC Part
### 1. Install adb-tools
Debian / Ubuntu / Mint / Pop!
```
sudo apt install adb
```
Arch Linux / Manjaro / Endeavour
```
sudo pacman -S android-tools
```
### 2. Start server
```
./altablet_server
```
or build it yourself with this command
```
cd server
gcc altablet.c adb_bridge.c -o altablet_server
```
Debug build:
```
gcc altablet.c adb_bridge.c -o debug -DDEBUG
./debug -r        # print polling rate
./debug -p        # print position data
./debug -r -p     # print both
```
If mouse not moving, try running with sudo.
```
sudo ./altablet_server
```

## Windows PC Part
### 1. Install adb-tools
#### Download and Install Android SDK Platform-Tools for Windows from the [official Android developer site](https://developer.android.com/tools/releases/platform-tools)


### 2. Start server
Run executable from [release page](https://github.com/Jibhong/ALTablet/releases)

or build it yourself with this command
```
cd server
gcc altablet.c adb_bridge.c -o altablet_server.exe -lws2_32
./altablet_server.exe
```
Debug build:
```
gcc altablet.c adb_bridge.c -o debug.exe -lws2_32 -DDEBUG
./debug.exe -r        # print polling rate
./debug.exe -p        # print position data
./debug.exe -r -p     # print both
```

## Android Part
1. Install apk file from the [release page](https://github.com/Jibhong/ALTablet/releases) or build it yourself
2. **Turn on usb debugging** in your android device
3. Connect your android device to your pc via USB
