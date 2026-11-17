declare module '*.vue' {
  import Vue from 'vue';
  export default Vue;
}

declare module 'is-promise'

declare module 'path' {
  export function join(...paths: string[]): string;
  export function resolve(...paths: string[]): string;
  export function basename(path: string, ext?: string): string;
  export function dirname(path: string): string;
  export function extname(path: string): string;
  export function isAbsolute(path: string): boolean;
  export function relative(from: string, to: string): string;
  const path: any;
  export default path;
}

declare module '@/config.json'