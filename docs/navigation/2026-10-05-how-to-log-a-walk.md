# How to log a walk

For the owner. One page. Dispatch -532 (walk logger), RECORD -533, -559, -560.

**What it is.** A test build of Forager with a "walk logger" in it. While you record a track, it writes down everything the phone senses: every location reading (GPS and network), what each satellite looks like, the raw satellite signals, steps, which way the phone is turned and moving, air pressure, and the battery every minute. It goes to a file on the phone. It changes nothing you see or hear on the walk.

**The two phones.**

| Phone | What its files show |
|---|---|
| S22 Ultra (no SIM) | The **no-network** case: GPS on its own, as a phone in the woods without signal. |
| S26 Ultra (SIM, mobile data) | The **with-network** case: GPS plus whatever the mobile network adds. |

Each file says at the top which phone it came from and whether it had a SIM and data, so the two can't be mixed up.

**The switch.** The logger runs only while its switch is on. It is off until you turn it on, and it stays as you left it.

- **Where:** Forager, **Settings** tab, **Diagnostics (debug build)**, the **Walk logger** switch near the top.
- **Or over USB:** with the phone plugged into the laptop, the coder can turn it on for you by driving the screen. You watch it happen.
- It takes effect at the **next** recording you start, not one already running.
- The S26 is your everyday phone. Turn the switch **off** after a logged walk if you don't want every recording logged. While it's on, the phone stays partly awake during recordings, which costs battery (the desk run measures how much), and each two-hour walk makes a file of very roughly 100 to 160 MB.

## Before you leave

1. Both phones charged.
2. On each phone, check the **Walk logger** switch is **on** (Settings, Diagnostics).
3. Close the phone's mainstream maps app and any other navigation app on both phones, so each walk's data stays comparable. (Another navigation app can switch the GPS into a different mode, which changes what the log records.)
4. Each phone needs at least 200 MB free. If it runs short mid-walk, the logger stops on its own and says so in its file. The recording carries on regardless.

## During the walk

1. Start a recording on **both** phones, the way you always do.
2. Carry them however you like. If you can, carry them the same way each walk (for example, one in each front pocket), so walks compare with each other.
3. Screen off, app swiped away, phone in a pocket: all fine. The logger keeps going as long as the recording does.

## After the walk

1. Stop the recording on both phones. The logger stops with it.
2. Bring both phones to the laptop and plug them in by USB.
3. The coder copies the files off. Nothing on the phone is deleted. For the record, the copy is:

   ```
   adb -s <phone> pull /sdcard/Android/data/com.zynergylabs.forager.app/files/walklogs/ ~/Zynergy/device-evidence/<date>-walk-logger/<model>/
   ```

   Each phone's files go in their own folder (`SM-S908U` for the S22, `SM-S948U` for the S26). The files hold positions, so they never go into the repository.
4. If you're done logging for now, turn the switch **off** on the S26.

**One file per recording**, named by when it started, for example `walklog-20261006T153012Z.txt` (the time is UTC).
