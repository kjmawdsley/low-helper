# Low Helper

Low Helper is a personal low-glucose decision aid.

## Android app
The Android build reads recent glucose values directly from GlucoDataHandler's local web service at `127.0.0.1:17580`, calculates short-term glucose movement locally, and combines that with manually entered IOB.

**Privacy:** glucose readings stay on the phone. The Android app does not send glucose, IOB, ICR, ISF, or recovery-target values to GitHub Pages or any external API.

### Phone setup
1. In GlucoDataHandler, enable the local/xDrip web service.
2. Install the Low Helper Android APK.
3. On first launch, enter ICR, ISF and recovery target once and save them locally.
4. Enter current Omnipod IOB when using the calculator.

The calculator is an experimental personal decision aid and is not a substitute for a hypo treatment plan or clinical advice.
