package com.myway5.www.Urlpool;

import java.io.IOException;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * 使用redis数据库进行持久化的类，同时也可以通过redis数据进行分布式的爬虫开发
 * redis采用的是单进程，单线程模式，多个客户端的访问会变成队列形式的访问，因此不存在多客户端之间
 * 并发访问的冲突问题，但是单个客户端的多连接之间可能会导致连接问题，所以在读写时需要池化,并且保证获取
 * Jedis对象时是synchronized
 * 
 * redis数据库中维持一个集合(set,set中字符串唯一)和一个列表(linked-list)
 * totalSet:所有的的url，用来保证加入的url不会重复
 * leftList:用来加入新的url(rpush)和弹出要使用的url(lpop)
 * @author jiang
 *
 */
public class RedisUrlPool extends AbstUrlPool implements IPersistence{
	private JedisPool jedisPool;
	private static RedisUrlPool urlPool;
	
	private RedisUrlPool(String host,int port){
		jedisPool = new JedisPool(new JedisPoolConfig(),host,port);
	}
	
	public static RedisUrlPool getInstance(String host,int port){
		if(urlPool == null){
			urlPool = new RedisUrlPool(host, port);
		}
		return urlPool;
	}
	
	public static RedisUrlPool getThreadSafeInstance(String host,int port){
		return RedisUrlPoolHolder.get(host, port);
	}
	
	public static class RedisUrlPoolHolder{
		public static RedisUrlPool urlPool;
		public static RedisUrlPool get(String host,int port){
			return urlPool = new RedisUrlPool(host, port);
		}
	}
	
	synchronized public void push(String url){
		Jedis jedis = null;
		try{
			jedis = jedisPool.getResource();
			if(!jedis.sismember("totalSet", url)){
				jedis.sadd("totalSet", url);		//如果不存在，就加入
				jedis.rpush("leftList", url);
				totalCount.incrementAndGet();
				leftUrlCount.incrementAndGet();
			}
		} catch (Exception e) {  
            e.printStackTrace();  
        } finally {  
            if (null != jedis) {  
                //释放已经用过的连接  
                jedis.close();   
            }  
        }  
	}
	
	synchronized public String pull(){
		Jedis jedis = null;
		String url = null;
		try{
			jedis = jedisPool.getResource();
			url = jedis.lpop("leftList");
			leftUrlCount.decrementAndGet();
		} catch (Exception e) {  
            e.printStackTrace();  
        } finally {  
            if (null != jedis) {  
                //释放已经用过的连接  
                jedis.close();   
            }  
        }  
		return url;
	}
	

	/**
	 * 释放redis的资源
	 */
	public void close() throws IOException {
		 jedisPool.destroy(); 
	}

}
