/**
 * Dates as Arc stores them.
 *
 * Every date in Arc — 시작일, 최종 종료일, a 세부항목's 완료예정일 — is epoch milliseconds at the
 * *local* start of day: the Android app normalizes each write through
 * `toStartOfDayMillis()`, which uses the device's own `ZoneId.systemDefault()`.
 *
 * This server does not run on the phone, and on Cloud Run it runs in UTC. Taking Node's local zone
 * would put midnight nine hours late for a Seoul user, which reads on screen as the day after the
 * one the assistant was told — and 남은날짜 counts from it, so a D-1 would show as D-0. Hence an
 * explicit zone, converted here rather than anywhere else.
 */

/** Milliseconds the given zone is ahead of UTC at that instant. */
function zoneOffset(instant: number, timeZone: string): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).formatToParts(new Date(instant));

  const read = (type: string): number => Number(parts.find((p) => p.type === type)?.value ?? '0');
  // Hour comes back as 24 at midnight under hour12:false, which Date.UTC would roll into the next
  // day, so it is folded back to 0.
  const asUtc = Date.UTC(
    read('year'),
    read('month') - 1,
    read('day'),
    read('hour') % 24,
    read('minute'),
    read('second'),
  );
  return asUtc - instant;
}

/**
 * Epoch millis of midnight on `isoDate` (YYYY-MM-DD) in `timeZone`.
 *
 * Applied twice: the offset is sampled at a guessed instant, and a zone that changed offset between
 * the guess and the result — a DST boundary — gives a different answer the second time. Korea has
 * no DST, so the second pass is a no-op there and the correctness is for everyone else.
 */
export function startOfDayMillis(isoDate: string, timeZone: string): number {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate.trim());
  if (!match) throw new Error(`날짜는 YYYY-MM-DD 형식이어야 합니다: "${isoDate}"`);

  const [, year, month, day] = match as unknown as [string, string, string, string];
  const wallClock = Date.UTC(Number(year), Number(month) - 1, Number(day));

  let instant = wallClock - zoneOffset(wallClock, timeZone);
  instant = wallClock - zoneOffset(instant, timeZone);
  return instant;
}

/** The inverse, for handing stored items back to the assistant in a form it can reason about. */
export function toIsoDate(millis: number, timeZone: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(millis));
  const read = (type: string): string => parts.find((p) => p.type === type)?.value ?? '';
  return `${read('year')}-${read('month')}-${read('day')}`;
}

/** Today in the configured zone, which is what "오늘" means to the person holding the phone. */
export function todayIso(timeZone: string): string {
  return toIsoDate(Date.now(), timeZone);
}
