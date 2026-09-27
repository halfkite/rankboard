/** Preserve page preferences while replacing the selected server in the URL. */
export function serverSwitchUrl(siteUrl: string, currentUrl: string): string {
  const destination = new URL(siteUrl, currentUrl);
  const params = new URL(currentUrl).searchParams;
  params.delete("server");
  destination.searchParams.forEach((value, key) => params.set(key, value));
  destination.search = params.toString();
  return destination.href;
}
