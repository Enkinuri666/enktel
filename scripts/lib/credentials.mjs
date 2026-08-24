/**
 * Panel line credentials, kept in one place because two tools now need them
 * and both must handle them the same way.
 *
 * The rules that matter: credentials come from the environment, a dotenv-style
 * file, or explicit flags — never from anything committed — and the password is
 * redacted out of every report and every error trace, because an Xtream stream
 * URL embeds it and a stack trace happily prints the URL.
 */

/** Environment variable per credential field. */
export const ENV_KEYS = {
  server: 'XTREAM_SERVER',
  username: 'XTREAM_USERNAME',
  password: 'XTREAM_PASSWORD',
};

/**
 * @param {Record<string, string|undefined>} env
 * @returns {{server: string, username: string, password: string}}
 */
export function credentialsFromEnv(env = {}) {
  return {
    server: env[ENV_KEYS.server] ?? '',
    username: env[ENV_KEYS.username] ?? '',
    password: env[ENV_KEYS.password] ?? '',
  };
}

/**
 * Minimal dotenv reader — only the three keys these tools need.
 *
 * Kept pure so it can be tested without a file: parsing is where the bugs are
 * (`export FOO=`, quoted values, comments), not reading.
 *
 * @param {string} text
 * @returns {{server?: string, username?: string, password?: string}}
 */
export function parseEnvFile(text) {
  const byEnvKey = Object.fromEntries(Object.entries(ENV_KEYS).map(([field, key]) => [key, field]));
  const out = {};

  for (const line of String(text ?? '').split(/\r?\n/)) {
    if (/^\s*#/.test(line)) continue;
    const m = /^\s*(?:export\s+)?([A-Z0-9_]+)\s*=\s*(.*)$/.exec(line);
    if (!m) continue;
    const field = byEnvKey[m[1]];
    if (!field) continue;
    out[field] = m[2].trim().replace(/^(['"])(.*)\1$/, '$2');
  }

  return out;
}

/**
 * Fill only the fields the caller has not already set.
 *
 * Precedence is explicit flags, then the environment, then the file — so a
 * `--env-file` never silently overrides a flag someone passed deliberately.
 *
 * @param {object} base
 * @param {object} extra
 * @returns {{server: string, username: string, password: string}}
 */
export function mergeCredentials(base, extra) {
  const merged = { ...base };
  for (const field of Object.keys(ENV_KEYS)) {
    if (!merged[field] && extra?.[field]) merged[field] = extra[field];
  }
  return merged;
}

/**
 * Which credential fields are still missing.
 *
 * @param {object} credentials
 * @returns {string[]} environment variable names
 */
export function missingCredentials(credentials) {
  return Object.entries(ENV_KEYS)
    .filter(([field]) => !credentials?.[field])
    .map(([, key]) => key);
}

/**
 * Keep a password out of error output.
 *
 * Only for free text — panel errors, stack traces — never for structured
 * output: a blind replace over JSON will happily eat a substring of a key, and
 * a one-character password turns "expectations" into "ex••••ectations". Short
 * passwords are left alone for the same reason; reports and catalog files are
 * kept credential-free by construction instead.
 *
 * @param {string} text
 * @param {string} password
 * @returns {string}
 */
export function redact(text, password) {
  if (!password || password.length < 6) return text;
  return String(text).split(password).join('••••');
}
