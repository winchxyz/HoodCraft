import { NextResponse, type NextRequest } from "next/server";

/**
 * Security headers, and the reason they matter here more than on a normal site.
 *
 * This site cannot move anyone's funds: it only ever calls `personal_sign`,
 * which produces a signature over a text message and can authorise no transfer
 * and no spend. There is no `eth_sendTransaction` anywhere in the codebase, and
 * the server holds no keys.
 *
 * The real risk is not that this code steals funds. It is that someone who can
 * inject a script into this page could ask a visitor's wallet for a *different*
 * thing - a transaction, or a token approval - and visitors who are used to
 * signing here might approve it. That is how wallet drainers actually work:
 * they do not break cryptography, they get a person to sign something.
 *
 * So the job of this file is to make injecting a script as hard as possible.
 */

export function proxy(request: NextRequest) {
  const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
  const isDev = process.env.NODE_ENV === "development";

  const csp = [
    "default-src 'self'",
    // The important one. 'strict-dynamic' means only scripts carrying this
    // request's nonce run, plus whatever they themselves load - so an injected
    // <script> tag is inert even if an attacker gets HTML into the page.
    // React needs eval in development only; production has no 'unsafe-eval'.
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${isDev ? " 'unsafe-eval'" : ""}`,
    // 'unsafe-inline' here covers JSX style={{...}} attributes. It is a real
    // loosening, but a style cannot call a wallet - script-src is the control
    // that stops a drainer, and that one stays strict.
    "style-src 'self' 'unsafe-inline'",
    // data: covers wallet icons, which EIP-6963 delivers as data URIs.
    "img-src 'self' blob: data:",
    // Fonts are self-hosted at build time by next/font, so no Google origin.
    "font-src 'self'",
    // The page only ever fetches its own /api routes. No third-party endpoint
    // can be reached, so nothing can be exfiltrated to one either.
    // ws: is development-only, for the hot-reload socket.
    `connect-src 'self'${isDev ? " ws:" : ""}`,
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    // Stops the page being framed, which is the setup for clickjacking a
    // wallet prompt.
    "frame-ancestors 'none'",
    "upgrade-insecure-requests",
  ].join("; ");

  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", nonce);
  requestHeaders.set("Content-Security-Policy", csp);

  const response = NextResponse.next({ request: { headers: requestHeaders } });

  // Set CSP_REPORT_ONLY=true to observe violations without breaking the page.
  // Worth doing on the first deploy to a new domain: if a wallet fails to
  // appear, the browser console names the directive that blocked it.
  const header =
    process.env.CSP_REPORT_ONLY === "true"
      ? "Content-Security-Policy-Report-Only"
      : "Content-Security-Policy";
  response.headers.set(header, csp);

  response.headers.set("X-Content-Type-Options", "nosniff");
  response.headers.set("X-Frame-Options", "DENY");
  response.headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
  // payment=() disables the Payment Request API outright: nothing on this site
  // should ever be asking for money.
  response.headers.set(
    "Permissions-Policy",
    "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
  );
  if (!isDev) {
    response.headers.set(
      "Strict-Transport-Security",
      "max-age=63072000; includeSubDomains; preload",
    );
  }

  return response;
}

export const config = {
  matcher: [
    {
      // Static assets and image-optimiser output need no CSP; prefetches are
      // skipped so they do not burn a nonce that never renders.
      source: "/((?!_next/static|_next/image|favicon.ico).*)",
      missing: [
        { type: "header", key: "next-router-prefetch" },
        { type: "header", key: "purpose", value: "prefetch" },
      ],
    },
  ],
};
