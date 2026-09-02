import type { NextConfig } from "next";
import path from "node:path";

const nextConfig: NextConfig = {
  // Without this, Turbopack walks up past the repo looking for a lockfile and
  // finds an unrelated one in the home directory.
  turbopack: { root: path.resolve(process.cwd()) },
};

export default nextConfig;
