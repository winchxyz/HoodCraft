import type { Metadata } from "next";
import { Silkscreen, IBM_Plex_Sans, IBM_Plex_Mono } from "next/font/google";
import "./globals.css";

/*
  Three families, two jobs. Silkscreen is a pixel face and carries the display
  voice; it is never used for running text, because a pixel font at paragraph
  length is a wall. IBM Plex does the reading — the mono cut is there because
  this site is full of addresses and block numbers, and those want tabular
  digits and unambiguous 0/O.
*/
const silkscreen = Silkscreen({
  weight: ["400", "700"],
  subsets: ["latin"],
  variable: "--font-silkscreen",
  display: "swap",
});

const plexSans = IBM_Plex_Sans({
  weight: ["400", "500", "600"],
  subsets: ["latin"],
  variable: "--font-plex-sans",
  display: "swap",
});

const plexMono = IBM_Plex_Mono({
  weight: ["400", "500"],
  subsets: ["latin"],
  variable: "--font-plex-mono",
  display: "swap",
});

export const metadata: Metadata = {
  // Falls back to localhost in development; set APP_ORIGIN for a real deployment.
  metadataBase: new URL(process.env.APP_ORIGIN ?? "http://localhost:3000"),
  title: "HoodCraft — Vote the next mascot",
  description:
    "The pet egg is built and locked, because one mob cannot hatch into itself. Hold the token, sign, and pick which mascot unlocks it.",
  openGraph: {
    title: "HoodCraft — Vote the next mascot",
    description: "Follow the bird. Hatch your mascot.",
    images: ["/brand/social-preview.jpg"],
    type: "website",
  },
  twitter: { card: "summary_large_image" },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${silkscreen.variable} ${plexSans.variable} ${plexMono.variable}`}>
      <body>{children}</body>
    </html>
  );
}
