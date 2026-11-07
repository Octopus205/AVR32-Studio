# AVR32 Studio PoC

Android prototype targeting ATmega32A / ATmega32 at 8 MHz.

## Current status

This first milestone provides the Android app structure, a basic source editor, and a GitHub Actions debug-APK build. **It does not yet include an Android ARMv7 AVR-GCC toolchain**, so Compile currently saves the source and reports that the compiler is missing; it does not produce real HEX/ELF files yet.

## Build

GitHub Actions runs `gradle assembleDebug` on pushes to `main` and manual workflow dispatches. Download the `AVR32-Studio-debug` artifact from the completed workflow run.

## Planned milestones

1. Integrate a compatible ARMv7 AVR-GCC/binutils toolchain.
2. Add ATmega32A/ATmega32 target selection and correct device headers.
3. Export HEX/ELF through Android's document picker.
4. Evaluate USBasp upload support over Android USB host.
