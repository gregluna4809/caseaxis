export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '-';
  return new Date(iso).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '-';
  return new Date(iso).toLocaleString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatPhoneNumber(value: string | null | undefined): string {
  if (!value?.trim()) return '-';

  const trimmed = value.trim();
  const extensionMatch = trimmed.match(/\b(?:ext\.?|x|extension)\s*(\d{1,5})\b/i);
  const extension = extensionMatch?.[1];
  const withoutExtension = extensionMatch
    ? trimmed.slice(0, extensionMatch.index).trim()
    : trimmed;
  const digits = withoutExtension.replace(/\D/g, '');

  if (digits.length === 11 && digits.startsWith('1')) {
    return appendExtension(formatUsPhone(digits.slice(1)), extension);
  }

  if (digits.length === 10) {
    return appendExtension(formatUsPhone(digits), extension);
  }

  if (trimmed.startsWith('+') && digits.length >= 8 && digits.length <= 15) {
    return appendExtension(`+${digits}`, extension);
  }

  return trimmed;
}

function formatUsPhone(digits: string): string {
  return `(${digits.slice(0, 3)}) ${digits.slice(3, 6)}-${digits.slice(6)}`;
}

function appendExtension(phone: string, extension: string | undefined): string {
  return extension ? `${phone} ext. ${extension}` : phone;
}

export function truncate(str: string | null | undefined, len = 8): string {
  if (!str) return '-';
  return str.length > len ? `${str.slice(0, len)}...` : str;
}

export function displayActor(value: string | null | undefined): string {
  return value ? 'CaseAxis user' : 'Unassigned';
}
