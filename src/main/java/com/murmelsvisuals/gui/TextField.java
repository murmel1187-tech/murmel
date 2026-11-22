package com.murmelsvisuals.gui;

import com.murmelsvisuals.util.Keys;

/** Minimal single-line text input state (the drawing lives in {@link Ui}). */
final class TextField {
	String text = "";
	int cursor;
	int maxLen = 96;

	void set(String s) {
		text = s == null ? "" : s;
		cursor = text.length();
	}

	void clear() {
		set("");
	}

	void type(int codepoint) {
		if (codepoint < 32 || codepoint == 127 || text.length() >= maxLen) return;
		String ch = new String(Character.toChars(codepoint));
		text = text.substring(0, cursor) + ch + text.substring(cursor);
		cursor += ch.length();
	}

	void paste(String s) {
		if (s == null) return;
		for (int i = 0; i < s.length() && text.length() < maxLen; ) {
			int cp = s.codePointAt(i);
			i += Character.charCount(cp);
			if (cp >= 32 && cp != 127) type(cp);
		}
	}

	/** @return true if the key was consumed. */
	boolean key(int key, int mods, String clipboard) {
		boolean ctrl = Keys.ctrl(mods);
		if (key == Keys.BACKSPACE) {
			if (cursor > 0) { text = text.substring(0, cursor - 1) + text.substring(cursor); cursor--; }
			return true;
		}
		if (key == Keys.DELETE) {
			if (cursor < text.length()) text = text.substring(0, cursor) + text.substring(cursor + 1);
			return true;
		}
		if (key == Keys.LEFT) { cursor = Math.max(0, cursor - 1); return true; }
		if (key == Keys.RIGHT) { cursor = Math.min(text.length(), cursor + 1); return true; }
		if (key == Keys.HOME) { cursor = 0; return true; }
		if (key == Keys.END) { cursor = text.length(); return true; }
		if (key == Keys.V && ctrl) { paste(clipboard); return true; }
		if (key == Keys.A && ctrl) { cursor = text.length(); return true; }
		return false;
	}
}
