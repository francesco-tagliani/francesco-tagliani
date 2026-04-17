# WhatsApp Sticker Uploader

An Android app that automatically loads sticker files from your WhatsApp Media folder and helps you add them to WhatsApp as sticker packs.

## Features

✅ **Automatic Loading**: Scans the WhatsApp stickers folder for all WebP files
✅ **Auto Grouping**: Groups stickers into packs of 30 (WhatsApp requirement)
✅ **Smart Filtering**: Filters out animated WebP and oversized files
✅ **Easy Integration**: One-click "Add to WhatsApp" button
✅ **Bulk Operations**: Add all sticker packs to WhatsApp at once
✅ **Extensive Logging**: Full debug logging for troubleshooting
✅ **Background Processing**: File copying happens in background, UI stays responsive

## How It Works

1. **Loads stickers** from `/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers`
2. **Groups into packs** of up to 30 stickers per pack
3. **Copies files** to app's private storage (required for WhatsApp integration)
4. **Shows UI** with list of available packs
5. **Sends intent** to WhatsApp when user clicks "Add to WhatsApp"
6. **WhatsApp** receives the sticker pack and allows user to add it

## Requirements

- Android 5.0 (API 21) or newer
- WhatsApp installed
- Sticker files in WebP format (512×512px, max 100KB each)
- At least 3 stickers per pack

## Quick Start

### Building

See [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md) for detailed build instructions.

Quick version:
```bash
cd whatsapp-sticker-uploader
./gradlew assembleDebug
```

###Installation

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Usage

1. Make sure you have WebP sticker files in the WhatsApp Media folder
2. Open the Sticker Uploader app
3. Wait for stickers to load (may take a few seconds for large folders)
4. Tap "Add to WhatsApp" next to any pack
5. Confirm in WhatsApp to add the stickers

## Architecture

### Components

- **MainActivity**: Main UI, handles button clicks, manages file copying
- **StickerContentProvider**: Serves sticker data and files to WhatsApp
- **StickerPackAdapter**: RecyclerView adapter for displaying packs
- **StickerPackLoader**: Scans filesystem and groups stickers into packs

### Data Flow

```
File System
    ↓
StickerPackLoader (scans, groups, filters)
    ↓
MainActivity (shows UI, copies to filesDir)
    ↓
filesDir/stickers/ (private app storage)
    ↓
StickerContentProvider (serves to WhatsApp)
    ↓
WhatsApp (displays to user)
```

## Debugging

View detailed logs:
```bash
adb logcat | grep "Sticker\|MainActivity\|Provider"
```

Logs include:
- File discovery and grouping
- UI updates
- Background file operations
- ContentProvider queries
- WhatsApp intent sends

## Permissions

- `MANAGE_EXTERNAL_STORAGE` - Access WhatsApp Media folder (Android 11+)
- `READ_EXTERNAL_STORAGE` - Access WhatsApp Media folder (Android 10 and below)

No internet or tracking permissions required.

## Limitations

- Maximum 30 stickers per pack (WhatsApp requirement)
- Minimum 3 stickers per pack (WhatsApp requirement)
- Maximum 100KB per sticker file (WhatsApp requirement)
- Only WebP format supported
- Animated WebP files are skipped

## Troubleshooting

### "No stickers found"
- Check that sticker files are in the correct folder
- Ensure files have `.webp` extension
- Make sure you've granted storage permissions

### "Unable to add to WhatsApp"
- Verify WhatsApp is installed
- Check that stickers are valid WebP format
- Ensure file sizes are under 100KB
- Try restarting WhatsApp

### App crashes
- Check adb logcat for error messages
- Ensure Android 5.0+ is installed
- Try clearing app data and restarting

## License

This project is provided as-is for personal use.

## Version

Version 1.0 - Initial release

