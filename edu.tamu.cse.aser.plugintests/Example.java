public class Example {
	static int x=0,y=0;
	static int z=0;
	static Object lock = new Object();
	static Object lock3 = new Object();
	public static void main(String[] args){
		MyThread t = new MyThread();
		t.start();
		synchronized(lock3)
		{
			y = 1;
			synchronized (lock)
			{
				x = 1;
			}
		}
		try{
			t.join();
			System.out.println(1/z);
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	static class MyThread extends Thread{
		public void run(){
			Object lock2 = lock;
			int r1,r2;
			synchronized (lock)
			{
				r1 = y;
				synchronized(lock3)
				{
					r2 =x;
					System.out.println(y);
				}
			}

			if(r1+r2!=1){
				z=1;
			}
		}
	}
}