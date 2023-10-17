package util;

public class MaskExample {
	
	public static void dataflow() {
		double x = Math.random();
		int y;
		if ( x/*mask*/ < .5) {
			y = (int) x/*mask*/;
		} else {
			y = 0;
		}
		System.err.println(y/*mask*/);
	}

	public static void controlflow() {
		double x = Math.random();
		int y = 10;
		while/*mask*/ (y > .2) {
			if/*mask*/ ( x < .5 ) {
				y--;
			} else {
				y = 0;
			}
		}
		System.err.println(y/*mask*/);
	}

	public static int/*mask*/ returnType() {
		double x = Math.random();
		int y;
		if ( x/*mask*/ < .5) {
			y = (int) x/*mask*/;
		} else {
			y = 0;
		}
		return y;
	}

	public static int returnExpr() {
		double x = Math.random();
		int y;
		if ( x/*mask*/ < .5) {
			y = (int) x/*mask*/;
		} else {
			y = 0;
		}
		return y/*mask*/;
	}

	public static int apiNames() {
		double x = Math.random/*mask*/();
		int y;
		if ( x < .5) {
			y = (int) x;
		} else {
			y = 0;
		}
		return y;
	}

	interface X {
		int doit();
	}
	static class Y implements X/*mask*/ {
		public int doit() {
			double x = Math.random();
			int y;
			if ( x < .5) {
				y = (int) x;
			} else {
				y = 0;
			}
			return y;
		}
	}

	public static void constants() {
		double x = 3.5;
		int y;
		if ( x < .5) {
			y = (int) x-1;
		} else {
			y = 0;
		}
		System.err.println(y/*mask*/);
	}

	public static void phi() {
		double x = Math.random();
		double y = 0;
		if ( x < .5) {
			y = x;
		} else {
			y = 0;
		}
		assert y == 0/*mask*/ || y == x/*mask*/;
	}
	
}
