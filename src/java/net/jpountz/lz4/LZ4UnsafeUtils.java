package net.jpountz.lz4;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static net.jpountz.lz4.LZ4Utils.COPY_LENGTH;
import static net.jpountz.util.UnsafeUtils.NATIVE_BYTE_ORDER;
import static net.jpountz.util.UnsafeUtils.readByte;
import static net.jpountz.util.UnsafeUtils.readInt;
import static net.jpountz.util.UnsafeUtils.readLong;
import static net.jpountz.util.UnsafeUtils.readShort;
import static net.jpountz.util.UnsafeUtils.writeByte;
import static net.jpountz.util.UnsafeUtils.writeLong;
import static net.jpountz.util.UnsafeUtils.writeShort;

import java.nio.ByteOrder;

enum LZ4UnsafeUtils {
  ;

  static void safeArraycopy(byte[] src, int srcOff, byte[] dest, int destOff, int len) {
    final int fastLen = len & 0xFFFFFFF8;
    wildArraycopy(src, srcOff, dest, destOff, fastLen);
    for (int i = 0, slowLen = len & 0x7; i < slowLen; i += 1) {
      writeByte(dest, destOff + fastLen + i, readByte(src, srcOff + fastLen + i));
    }
  }

  static void wildArraycopy(byte[] src, int srcOff, byte[] dest, int destOff, int len) {
    for (int i = 0; i < len; i += 8) {
      writeLong(dest, destOff + i, readLong(src, srcOff + i));
    }
  }

  static void safeIncrementalCopy(byte[] dest, int matchOff, int dOff, int matchCopyEnd) {
    LZ4Utils.naiveIncrementalCopy(dest, matchOff, dOff, matchCopyEnd - dOff);
  }

  static void wildIncrementalCopy(byte[] dest, int matchOff, int dOff, int matchCopyEnd) {
    while (dOff - matchOff < COPY_LENGTH) {
      writeLong(dest, dOff, readLong(dest, matchOff));
      dOff += dOff - matchOff;
    }
    while (dOff < matchCopyEnd) {
      writeLong(dest, dOff, readLong(dest, matchOff));
      dOff += 8;
      matchOff += 8;
    }
  }

  static int readShortLittleEndian(byte[] src, int srcOff) {
    short s = readShort(src, srcOff);
    if (NATIVE_BYTE_ORDER == ByteOrder.BIG_ENDIAN) {
      s = Short.reverseBytes(s);
    }
    return s & 0xFFFF;
  }

  static void writeShortLittleEndian(byte[] dest, int destOff, int value) {
    short s = (short) value;
    if (NATIVE_BYTE_ORDER == ByteOrder.BIG_ENDIAN) {
      s = Short.reverseBytes(s);
    }
    writeShort(dest, destOff, s);
  }

  static int hash(byte[] buf, int off) {
    return LZ4Utils.hash(readInt(buf, off));
  }

  static int hash64k(byte[] buf, int off) {
    return LZ4Utils.hash64k(readInt(buf, off));
  }

  static boolean readIntEquals(byte[] src, int ref, int sOff) {
    return readInt(src, ref) == readInt(src, sOff);
  }

  static int commonBytes(byte[] src, int ref, int sOff, int srcLimit) {
    int matchLen = 0;
    while (sOff < srcLimit - 8) {
      final long diff = readLong(src, sOff) - readLong(src, ref);
      final int zeroBits;
      if (NATIVE_BYTE_ORDER == ByteOrder.BIG_ENDIAN) {
        zeroBits = Long.numberOfLeadingZeros(diff);
      } else {
        zeroBits = Long.numberOfTrailingZeros(diff);
      }
      if (zeroBits == 64) {
        matchLen += 8;
        sOff += 8;
        ref += 8;
      } else {
        final int inc = zeroBits >>> 3;
        matchLen += inc;
        sOff += inc;
        break;
      }
    }
    return matchLen;
  }

  static int writeLen(int len, byte[] dest, int dOff) {
    while (len >= 0xFF) {
      writeByte(dest, dOff++, 0xFF);
      len -= 0xFF;
    }
    writeByte(dest, dOff++, len);
    return dOff;
  }

}