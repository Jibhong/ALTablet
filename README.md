# ALTablet
ALTablet let you use your android tablet as a graphic tablet for your computer!

## ALTablet with [osu!](https://osu.ppy.sh/)

![](https://raw.githubusercontent.com/Jibhong/ALTablet/refs/heads/main/README/sample.webp)

# Features
|  |  Feature |
| --- | --- |
| ✅ | Control your mouse with your Android device's stylus |
| ✅ | Low latency input stream |
| ✅ | Customize tablet input area |
| ⏳ | Save custom input area |
| 🚫 | Stylus pressure support |

✅ Finished
⏳ Someday...
🚫 Not Planned

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
or build it your self with this command
```
cd server
gcc altablet.c adb_bridge.c -o altablet_server
```
If mouse not moving, try running with sudo.
```
sudo ./altablet_server
```

## Windows PC Part
```
cd server
gcc altablet.c adb_bridge.c -o altablet_server.exe -lws2_32
./altablet_server.exe
```

## Android Part
1. Install apk file from the [release page](https://github.com/Jibhong/ALTablet/releases) or build it your self
2. **Turn on usb debugging** in your android device
3. Connect your android device to your pc via USB
