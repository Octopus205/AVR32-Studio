# OctoAVR32A

**OctoAVR32A** is an offline AVR C/C++ microcontroller IDE for Android phones and tablets, engineered primarily for the **ATmega32A** (`atmega32a`, default `8 MHz`, `-Os`, ELF + Intel HEX).

---

## Key Capabilities

- **Offline AVR Cross-Compilation**: Compiles C, C++, and `.ino` sources directly on Android without a PC or cloud compiler using Bionic-compiled AVR-GCC (`7.3.0`) and Binutils (`avr-gcc`, `avr-g++`, `cc1`, `cc1plus`, `collect2`, `avr-as`, `avr-ld`, `avr-ar`, `avr-objcopy`, `avr-objdump`, `avr-size`) plus AVR-Libc headers, CRT (`crtatmega32a.o`), `libgcc.a`, `libc.a`, `libm.a`, and linker scripts.
- **ATmega32A Ready Out of the Box**:
  - Default target: `-mmcu=atmega32a`, `-DF_CPU=8000000UL`, `-Os`, ELF + Intel HEX output.
  - Built-in **10 ATmega32A Examples**: LED Blinking, GPIO I/O, Push-Button Toggle, External Interrupt INT0, Timer0 & Timer1 CTC, Hardware PWM Fade, UART Serial Echo, SPI Master, ADC 10-bit Conversion, and a Multi-File Modular HAL Project.
- **Embedded C/C++ Code Editor**:
  - C/C++/AVR register syntax highlighting (`DDRB`, `PORTB`, `TCCR0`, `ADCSRA`, `UCSRA`, `ISR`, etc.).
  - Synchronized line numbers, current line & error line highlighting, horizontal + vertical scrolling.
  - Undo/Redo history, Find & Replace, C/C++ code formatter, auto-closing brackets, auto-indentation, and a quick embedded symbol strip (`TAB`, `{`, `}`, `1 << `, `&`, `|`, `~`).
- **Verify, Build, Cancel, Export & Flash**:
  - **Verify**: Compiles all source units and navigates to compiler error lines on click or via Prev/Next console issue buttons.
  - **Build**: Produces `.elf`, Intel `.hex` (validated for record checksums & `:00000001FF` EOF), optional `.lss` disassembly, and real Flash/SRAM memory usage bars via `avr-size`.
  - **Firmware Inspector**: Inspect Flash/SRAM section breakdown, validate ELF/HEX headers, browse raw Intel HEX records, and run on-demand `avr-objdump -h -S` disassembly.
  - **ATmega32A Reference**: Built-in PDIP-40 pinout diagram, peripheral register cheat sheet, and one-tap C snippet insertion into the editor.
  - **Stop**: Immediately cancels active native compiler processes.
  - **Export & ZFlasher Integration**: Save `.hex` or `.elf` to Downloads via Android Storage Access Framework (SAF), or hand off the verified `.hex` via `FileProvider` directly to **ZFlasher AVR** (`ru.zdevs.zflasher.avr`).
- **Project Management & Hardware Inspection**:
  - Persistent workspaces under `filesDir/workspaces/` with New, Open, Save, Save As, Rename, Delete, Recent Projects, and multi-file (`src/`, `include/`) explorer.
  - Real `UsbManager` USB OTG enumeration for USBasp, AVRISP mkII, CH340, CP210x, FT232R, and Arduino CDC devices, plus UART configuration.

---

## Native Toolchain Architecture & Binary Deployment

Android 10+ enforces W^X (`noexec`) on `filesDir` and `cacheDir`. To execute `avr-gcc` and its helper binaries cleanly without root access:

1. **Native Executables (`app/src/main/jniLibs/<abi>/`)**:
   - All host executables are compiled with Android NDK against Bionic (`aarch64-linux-android24` for `arm64-v8a` and `x86_64-linux-android24` for `x86_64`) with GMP/MPFR/MPC and `libc++` statically linked and packaged as `libavr_*.so` (`packaging.jniLibs.useLegacyPackaging = true`).
   - Android extracts them to `applicationInfo.nativeLibraryDir` with `0755` execute permissions.
   - `AvrToolchainManager` creates symlinks in writable `filesDir/toolchain/bin/` and `filesDir/toolchain/libexec/gcc/avr/7.3.0/` pointing to `nativeLibraryDir/libavr_*.so` so `avr-gcc` resolves `cc1`, `cc1plus`, `collect2`, `as`, and `ld` natively.
2. **AVR Sysroot (`app/src/main/assets/avr_sysroot.zip`)**:
   - Contains target-independent `avr/include/`, `avr/lib/` (`crtatmega32a.o`, `libc.a`, `libm.a`, `ldscripts/`), and `lib/gcc/avr/7.3.0/` (`device-specs/`, `include/`, `libgcc.a`).
   - Extracted on first launch to writable `filesDir/toolchain/sysroot/`.

To rebuild or update the native binaries from source:

```bash
bash scripts/build_avr_toolchain_android.sh
```

---

## Building & Testing

```bash
# Run unit & Robolectric acceptance tests
./gradlew :app:testDebugUnitTest

# Build Debug APK
./gradlew :app:assembleDebug
```
