package com.shansun.demo.forkjoin;

import EDU.oswego.cs.dl.util.concurrent.FJTask;

/**
 * ʹ��fork-join��ʽ����PIֵ
 * 
 * @author: lanbo <br>
 * @version: 1.0 <br>
 * @date: 2012-7-4
 */
public class PI {

	static int		numSteps	= 100000;
	static double	step;
	static double	sum			= 0.0;
	static int		partStep;

	static class PITask extends FJTask {

		@Override
		public void run() {
			
		}
		
	}
	
	public static void main(String[] args) {

	}

}
