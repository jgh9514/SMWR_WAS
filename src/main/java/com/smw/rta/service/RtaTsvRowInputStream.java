package com.smw.rta.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

/**
 * {@code List<T>} 를 TAB 구분 CSV 스트림으로 변환하는 {@link InputStream}.
 * <p>
 * {@link java.io.ByteArrayInputStream} 으로 전체 데이터를 한 번에 직렬화하는 방식과 달리,
 * 행 단위로 바이트를 생성하므로 수백만 행이 있어도 힙 사용량이 O(1)에 가깝다.
 * <p>
 * PostgreSQL {@code CopyManager#copyIn(String, InputStream)} 에 직접 전달 가능.
 */
class RtaTsvRowInputStream<T> extends InputStream {

	private final List<T> rows;
	private final Function<T, byte[]> rowSerializer;

	private int rowIndex = 0;
	private byte[] currentRowBytes = null;
	private int pos = 0;

	/**
	 * @param rows          직렬화할 행 목록
	 * @param rowSerializer 행 → TAB 구분 한 줄({@code \n} 포함) UTF-8 바이트 배열 변환 함수
	 */
	RtaTsvRowInputStream(List<T> rows, Function<T, byte[]> rowSerializer) {
		this.rows = rows;
		this.rowSerializer = rowSerializer;
		advance();
	}

	private void advance() {
		if (rowIndex >= rows.size()) {
			currentRowBytes = null;
			return;
		}
		currentRowBytes = rowSerializer.apply(rows.get(rowIndex++));
		pos = 0;
	}

	@Override
	public int read() throws IOException {
		if (currentRowBytes == null) {
			return -1;
		}
		if (pos >= currentRowBytes.length) {
			advance();
			return read();
		}
		return currentRowBytes[pos++] & 0xff;
	}

	@Override
	public int read(byte[] buf, int off, int len) throws IOException {
		if (currentRowBytes == null) {
			return -1;
		}
		int written = 0;
		while (written < len) {
			if (pos >= currentRowBytes.length) {
				advance();
				if (currentRowBytes == null) {
					break;
				}
			}
			int avail = currentRowBytes.length - pos;
			int toWrite = Math.min(avail, len - written);
			System.arraycopy(currentRowBytes, pos, buf, off + written, toWrite);
			pos += toWrite;
			written += toWrite;
		}
		return written == 0 ? -1 : written;
	}

	/** TSV 행 끝에 {@code \n} 을 붙인 UTF-8 바이트 배열 반환. 탭·개행 포함 여부를 검증한다. */
	static byte[] tsvLine(String... fields) {
		StringBuilder sb = new StringBuilder(64);
		for (int i = 0; i < fields.length; i++) {
			if (i > 0) {
				sb.append('\t');
			}
			String f = fields[i];
			if (f != null && (f.indexOf('\t') >= 0 || f.indexOf('\n') >= 0 || f.indexOf('\r') >= 0)) {
				throw new IllegalArgumentException("COPY TSV 불가 — TAB/개행 포함: " + f);
			}
			sb.append(f);
		}
		sb.append('\n');
		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}
}
