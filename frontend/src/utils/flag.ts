const CURRENCY_TO_CC: Record<string, string> = {
  AED: 'AE', ARS: 'AR', AUD: 'AU', BDT: 'BD', BHD: 'BH', BND: 'BN',
  BRL: 'BR', CAD: 'CA', CHF: 'CH', CLP: 'CL', CNY: 'CN', COP: 'CO',
  CZK: 'CZ', DKK: 'DK', EGP: 'EG', ETB: 'ET', EUR: 'EU', GBP: 'GB',
  GHS: 'GH', HKD: 'HK', HUF: 'HU', IDR: 'ID', ILS: 'IL', INR: 'IN',
  ISK: 'IS', JOD: 'JO', JPY: 'JP', KES: 'KE', KHR: 'KH', KRW: 'KR',
  KWD: 'KW', LAK: 'LA', LKR: 'LK', MMK: 'MM', MOP: 'MO', MXN: 'MX',
  MYR: 'MY', NGN: 'NG', NOK: 'NO', NPR: 'NP', NZD: 'NZ', OMR: 'OM',
  PEN: 'PE', PHP: 'PH', PKR: 'PK', PLN: 'PL', QAR: 'QA', RON: 'RO',
  RUB: 'RU', SAR: 'SA', SEK: 'SE', SGD: 'SG', THB: 'TH', TRY: 'TR',
  TWD: 'TW', TZS: 'TZ', UAH: 'UA', UGX: 'UG', USD: 'US', VND: 'VN',
  ZAR: 'ZA',
};

function ccToFlag(cc: string): string {
  // EU uses a special code — use the EU flag emoji directly
  if (cc === 'EU') return '🇪🇺';
  return [...cc.toUpperCase()]
    .map((c) => String.fromCodePoint(c.charCodeAt(0) + 0x1f1a5))
    .join('');
}

export function currencyFlag(currency: string): string {
  const cc = CURRENCY_TO_CC[currency?.toUpperCase()];
  return cc ? ccToFlag(cc) : '';
}
