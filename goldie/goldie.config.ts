import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import type { GoldieConfig } from "goldie";

/**
 * Google Play screenshots and promo video for Reel Counter (goldie: npm i -g goldie).
 * Run from the repo root:  GOLDIE_CONFIG=goldie/goldie.config.ts goldie all
 *
 * Captures use the `screenshots` build type (./gradlew :app:assembleScreenshots): a release build
 * that seeds three weeks of sample history on first launch and skips onboarding. Flows live in
 * .argent/flows (store-*). Output goes to goldie/out/ (git-ignored).
 */
const HERE = dirname(fileURLToPath(import.meta.url));
const APP_ROOT = resolve(HERE, "..");
const APK = resolve(APP_ROOT, "app/build/outputs/apk/screenshots/app-screenshots.apk");
const APP_ID = "com.reelcounter.android.screenshots";
const FONTS = resolve(APP_ROOT, "core/design/src/main/res/font");

const config: GoldieConfig = {
  appRoot: APP_ROOT,
  // Android only; goldie still requires the iOS fields, so they point at the APK.
  appPath: APK,
  bundleId: APP_ID,
  android: { appPath: APK, applicationId: APP_ID },

  devices: ["pixel-10-pro"],
  locales: ["en-US"],
  appearance: "dark",
  // iPhone bezel, unused here (Android tiles get the bundled Pixel 10 Pro bezel), but goldie's
  // manifest step needs the field.
  frame: { variant: "17-pro-silver" },

  theme: {
    // The app's Midnight palette: warm near-black, warm off-white text. goldie draws linear gradients only.
    background: "linear-gradient(180deg, #2A251E 0%, #181614 45%, #111110 100%)",
    headlineColor: "#EDE8E1",
    subheadColor: "#A39D94",
    // The app's own type: headlines (weight 700) in Bricolage, subheads (400) in Figtree.
    // goldie uses one family for both, so the family maps each weight to a different file.
    fontFiles: [
      { family: "Reel Counter", files: { 400: `${FONTS}/figtree.ttf`, 700: `${FONTS}/bricolage.ttf` } },
    ],
    fontFamily: '"Reel Counter", sans-serif',
    copyHeightRatio: 0.24,
    deviceWidthRatio: 0.84,
    // Panorama opener, then a copy-led tile for every screen (no copy-less "minimal" breather).
    template: ["panorama", "hero", "offset", "hero", "tilt-right"],
    layout: "classic",
  },

  store: {
    name: "Reel Counter",
    subtitle: { "en-US": "Count reels, keep your brain bright" },
    developer: "Reel Counter",
    category: "Health & Fitness",
    rating: 4.8,
    ratingCount: "1.2K reviews",
    ageRating: "Everyone",
    price: "Free",
    description: {
      "en-US":
        "Reel Counter counts every Instagram Reel and YouTube Short you watch, right as you scroll. " +
        "No account, no screen recording.\n\n" +
        "Your brain shows the cost: it gets foggier with every reel and heals overnight when you stay under your limit. " +
        "Set a daily limit, get gentle nudges on the way, and watch the habit shrink week by week.",
    },
  },

  scenes: [
    {
      kind: "screenshot",
      id: "today",
      flow: "store-01-today",
      headline: { "en-US": "Count every reel" },
      subhead: { "en-US": "Instagram Reels and YouTube Shorts, counted as you scroll." },
    },
    {
      kind: "screenshot",
      id: "brain",
      flow: "store-02-brain",
      headline: { "en-US": "Meet your brain on reels" },
      subhead: { "en-US": "It fogs up as you scroll and heals overnight." },
    },
    {
      kind: "screenshot",
      id: "progress",
      flow: "store-03-progress",
      headline: { "en-US": "Watch the habit shrink" },
      subhead: { "en-US": "Every day and week, next to your limit." },
    },
    {
      kind: "screenshot",
      id: "when",
      flow: "store-04-when",
      headline: { "en-US": "Know when you scroll" },
      subhead: { "en-US": "Spot the late-night sittings and the app behind them." },
    },
    {
      kind: "screenshot",
      id: "limits",
      flow: "store-05-limits",
      headline: { "en-US": "A limit that fits you" },
      subhead: { "en-US": "Gentle nudges at 50, 80 and 100%." },
    },
    {
      kind: "preview",
      id: "preview",
      segments: [
        { id: "today", flow: "store-preview-01-today" },
        { id: "brain", flow: "store-preview-02-brain" },
        { id: "progress", flow: "store-preview-03-progress" },
        { id: "limits", flow: "store-preview-04-limits", holdSeconds: 1.5 },
      ],
    },
  ],
};

export default config;
