package com.skiResort.client;

import java.net.http.*;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

import com.google.gson.Gson;
import com.opencsv.CSVWriter;
import com.skiResort.client.entity.CSVData;
import com.skiResort.client.entity.SkiLiftRideEvent;



import java.io.FileWriter;
import java.io.IOException;
import java.net.*;

public class ClientTest {
	
	public static int MAX_RESEND = 5;

	public static void main(String[] args) {
		ClientTest test = new ClientTest();
		BodyHandler<String> handler = BodyHandlers.ofString();
		HttpClient httpClient = HttpClient.newBuilder()
	            .version(HttpClient.Version.HTTP_1_1)
	            .connectTimeout(Duration.ofSeconds(10))
	            .build();
		Gson gson = new Gson();
		Map<SkiLiftRideEvent,CSVData> reportData = new HashMap<>();
		
		ExecutorService pool = Executors.newFixedThreadPool(32);
		
		List<SkiLiftRideEvent> skiList = getSkiListRideEvents();
		List<List<SkiLiftRideEvent>> skiThreadList = new ArrayList<>();
		int i = 0;
		for(SkiLiftRideEvent event : skiList) {
			List<SkiLiftRideEvent> newList = null;
			if(i > skiThreadList.size()-1) {
				newList = new ArrayList<>();
				skiThreadList.add(newList);
			}else {
				newList = skiThreadList.get(i);	
			}
			i = i == 31 ? 0 : i+1 ;
			newList.add(event);
		}
		int successCount = 0;
		int failureCount = 0;
		
		Long start = System.currentTimeMillis();
		
		for(List<SkiLiftRideEvent> list : skiThreadList) {
			 Future<Map<String, Integer>> future = pool.submit(test.new ListThread(list,gson,httpClient, handler,reportData));
			 Map<String, Integer> resultMap = null;
			try {
				resultMap = future.get();
			} catch (InterruptedException | ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			int currentSuccess = resultMap.get("success") != null ? resultMap.get("success") : 0;
			int currentFailure = resultMap.get("failure") != null ? resultMap.get("failure") : 0;
			successCount += currentSuccess;
			failureCount += currentFailure;
		}
		
		Long timeTaken = System.currentTimeMillis() - start;
		
		float throughput = ((float)successCount + (float)failureCount) / (float)timeTaken;
		float totalRunTume = (float)timeTaken/1000;
		System.out.println("Number of successful requests sent: " + successCount);
		System.out.println("Number of unsuccessful requests sent: " + failureCount);
		System.out.println("Total run time (wall time) for all phases to complete: " + totalRunTume + " seconds");
		System.out.println("Total throughput in requests per second: " + throughput*1000);
		pool.shutdown();
		writeCSV(reportData);
		writeProfilingData(reportData.values());
	}

	private static void writeProfilingData(Collection<CSVData> values) {
		List<CSVData> list = new ArrayList<>(values);
		list.sort(Comparator.comparing(CSVData::getLatency));
		int size = list.size();
		long medianLatency = list.size()%2 == 0 ? (list.get(size/2).getLatency() +
				list.get((size/2)+1).getLatency())/2 : list.get(size/2).getLatency();
		long meanLatency = list.stream().mapToLong(data -> data.getLatency()).sum()/size;
		int index99 = size * 99 /100;
		long latency99Sum = 0l;
		for(int i = index99; i < size; i++) {
			latency99Sum += list.get(i).getLatency();
		}
		long meanLatency99 = latency99Sum/(size-index99);
		System.out.println("The mean latency is: " + meanLatency + " ms");
		System.out.println("The median latency is: " + medianLatency + " ms");
		System.out.println("The p99 (99th percentile) response time is: " + meanLatency99 + " ms");
		System.out.println("The min response time is: " + list.get(0).getLatency() + " ms");
		System.out.println("The max response time is: " + list.get(list.size()-1).getLatency() + " ms");
	}

	private static void writeCSV(Map<SkiLiftRideEvent, CSVData> reportData) {
		//Instantiating the CSVWriter class
	      CSVWriter writer;
		try {
			writer = new CSVWriter(new FileWriter("output-"+Date.valueOf(LocalDate.now()).toString()+".csv"));
			 //Writing data to a csv file
		      String line1[] = {"skierID", "resortID", "liftID", "seasonID", "dayID","time","latency","startTime","responseCode","requestMethod"};
		      //Writing data to the csv file
		      writer.writeNext(line1);
		      reportData.forEach((event,data)->{
		    	  String line[] = {String.valueOf(event.getSkierID()),String.valueOf(event.getResortID()),
		    			  String.valueOf(event.getLiftID()),String.valueOf(event.getSeasonID()),
		    			  String.valueOf(event.getDayID()),String.valueOf(event.getTime()),
		    			  String.valueOf(data.getLatency()),String.valueOf(data.getStartTime()),
		    			  String.valueOf(data.getResponseCode()),String.valueOf(data.getRequestMethod())};
		    	  writer.writeNext(line);
		      });
		      //Flushing data from writer to file
		      writer.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	     
	      System.out.println("Data entered");
	}

	private static void postData(HttpClient httpClient,SkiLiftRideEvent event,
			Gson gson, BodyHandler<String> handler,Map<String, Integer> resultMap, Map<SkiLiftRideEvent, CSVData> reportData) {
		HttpRequest request = HttpRequest.newBuilder()
				  .uri(URI.create("http://localhost:8081/skiers")) // Use IP and port from oracle cloud instance where java application is running
				  .header("Content-Type", "application/json")
				  .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(event)))
				  .build();
		
        HttpResponse<String> response;
		try {
	        java.util.Date date = Date.from(Instant.now());
	        long start = System.currentTimeMillis();
	        CompletableFuture<HttpResponse<String>> response1 = 
	        		httpClient.sendAsync(request, handler)
	                    .handleAsync((r, t) -> tryResend(httpClient, request, handler, 1, r, t,resultMap ))
	                    .thenCompose(Function.identity());
	        response = response1.get();
	        
	        CSVData data = new CSVData();
	        data.setStartTime(date);
	        data.setLatency(System.currentTimeMillis() - start);
	        data.setRequestMethod("POST");
	        data.setResponseCode(response.statusCode());
	        
	        reportData.put(event, data);
		} catch (InterruptedException | ExecutionException e) {
			resultMap.put("failure", 1);
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static boolean shouldRetry(HttpResponse<?> r, Throwable t, int count) {
	    if (r != null && r.statusCode() == 200 || count >= MAX_RESEND) return false;
	    if (t instanceof Object) return false;
	    return true;
	}

	public static <T> CompletableFuture<HttpResponse<T>>
	        tryResend(HttpClient client, HttpRequest request,
	                  BodyHandler<T> handler, int count,
	                  HttpResponse<T> resp, Throwable t,Map<String, Integer> resultMap) {
	    if (shouldRetry(resp, t, count)) {
	    	int failureCount = !resultMap.containsKey("failure") ? 1 : resultMap.get("failure") + 1;
	    	resultMap.put("failure", failureCount);
	        return client.sendAsync(request, handler)
	                .handleAsync((r, x) -> tryResend(client, request, handler, count + 1, r, x,resultMap))
	                .thenCompose(Function.identity());
	    } else if (t != null) {
	    	int failureCount = !resultMap.containsKey("failure") ? 1 : resultMap.get("failure") + 1;
	    	resultMap.put("failure", failureCount);
	        return CompletableFuture.failedFuture(t);
	    } else {
	    	int successCount = !resultMap.containsKey("success")  ? 1 : resultMap.get("success") + 1;
	    	resultMap.put("success",successCount);
	        return CompletableFuture.completedFuture(resp);
	    }
	}
	
	public static List<SkiLiftRideEvent> getSkiListRideEvents(){
		List<SkiLiftRideEvent> liftRideEvents = new ArrayList<>();
		Random random = new Random();
		for(int i = 0; i < 10000; i++) {
			SkiLiftRideEvent liftRideEvent = new SkiLiftRideEvent();
//			1. skierID - between 1 and 100000
			liftRideEvent.setSkierID(random.nextInt(99999) + 1);
//			2. resortID - between 1 and 10
			liftRideEvent.setResortID(random.nextInt(9) + 1);
//			3. liftID - between 1 and 40
			liftRideEvent.setLiftID(random.nextInt(39) + 1);
//			4. seasonID - 2022
			liftRideEvent.setSeasonID(2022);
//			5. dayID - 1
			liftRideEvent.setDayID(1);
//			6. time - between 1 and 360
			liftRideEvent.setTime(random.nextInt(359) + 1);
			liftRideEvents.add(liftRideEvent);
		}
		return liftRideEvents;
	}
	
	public class ListThread implements Callable<Map<String,Integer>>{

		private List<SkiLiftRideEvent> skiLiftRideEvents;
		private Gson gson;
		private HttpClient httpClient;
		private BodyHandler<String> handler;
		private Map<SkiLiftRideEvent, CSVData> reportData;
		
		public ListThread(List<SkiLiftRideEvent> skiLiftRideEvents, Gson gson, 
				HttpClient httpClient, BodyHandler<String> handler, Map<SkiLiftRideEvent, CSVData> reportData) {
			this.skiLiftRideEvents = skiLiftRideEvents;
			this.gson = gson;
			this.httpClient = httpClient;
			this.handler = handler;
			this.reportData = reportData;
		}
		
		@Override
		public Map<String,Integer> call() throws Exception {
			Map<String, Integer> resultMap = new HashMap<>();
			skiLiftRideEvents.forEach(event -> {
				postData(httpClient, event, gson, handler, resultMap,reportData);
			});
			return resultMap;
		}
		
	}
}
