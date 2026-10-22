// BRouter's checksum, btools.util.Crc32 at v1.7.10 (Crc32.java:9-16): the standard reflected CRC-32 table
// (polynomial 0xEDB88320), starting value 0xFFFFFFFF, and no final inversion. It is therefore the bitwise NOT
// of the usual CRC-32. Returned as a signed 32-bit int, as Java's int is, so it compares and writes the way
// BRouter's DataOutputStream.writeInt does.

const TABLE: Int32Array = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? (c >>> 1) ^ 0xedb88320 : c >>> 1;
    table[n] = c;
  }
  return table;
})();

export function brouterCrc(bytes: Uint8Array): number {
  let crc = -1; // 0xFFFFFFFF
  for (let i = 0; i < bytes.length; i++) {
    crc = (crc >>> 8) ^ TABLE[(crc ^ bytes[i]) & 0xff];
  }
  return crc | 0;
}
