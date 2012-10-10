package com.shansun.demo.future;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author: lanbo <br>
 * @version: 1.0 <br>
 * @date: 2012-8-15
 */
public class Demo {

	static ExecutorService	service	= Executors.newFixedThreadPool(1);

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		service.submit(new Runnable() {
			
			@Override
			public void run() {
				
			}
		});
	}

}
