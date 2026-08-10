import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const cwd = resolve(root, 'apps/risk-core');
const command = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
const child = spawn(command, process.argv.slice(2), { cwd, stdio: 'inherit', shell: process.platform === 'win32' });
child.once('error', (error) => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; });
child.once('exit', (code) => { process.exitCode = code ?? 1; });
