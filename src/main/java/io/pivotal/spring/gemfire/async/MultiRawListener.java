package io.pivotal.spring.gemfire.async;

import com.gemstone.gemfire.cache.*;
import com.gemstone.gemfire.cache.asyncqueue.AsyncEvent;
import com.gemstone.gemfire.cache.asyncqueue.AsyncEventListener;
import com.gemstone.gemfire.pdx.JSONFormatter;
import com.gemstone.gemfire.pdx.PdxInstance;
import com.gemstone.org.json.JSONException;
import com.gemstone.org.json.JSONObject;

import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.Region;

import java.util.*;

import io.pivotal.spring.gemfire.async.lib.RegionProcessor;

/**
 * Created by lei_xu on 7/17/16.
 */
public class MultiRawListener implements AsyncEventListener, Declarable {

    private GemFireCache gemFireCache;
    private Region regionCount;
//    private Region regionRaw = gemFireCache.getRegion("RegionRaw");
    private Region<Integer, PdxInstance> regionTop;
    private Region regionTopTen;

    @Override
    public boolean processEvents(List<AsyncEvent> events) {

//        GemFireCache gemFireCache = CacheFactory.getAnyInstance();
//        Region regionCount = gemFireCache.getRegion("RegionCount");
//        Region<Integer, PdxInstance> regionTop = gemFireCache.getRegion("RegionTop");
//        Region regionTopTen = gemFireCache.getRegion("RegionTopTen");

        gemFireCache = CacheFactory.getAnyInstance();
        regionCount = gemFireCache.getRegion("RegionCount");
        regionTop = gemFireCache.getRegion("RegionTop");
        regionTopTen = gemFireCache.getRegion("RegionTopTen");

        RegionProcessor processor = new RegionProcessor(regionCount, regionTop, regionTopTen);

        Integer smallestToptenCount = 0;
        Boolean isProcessTopTen = false;

        PdxInstance topTenValue = (PdxInstance)regionTopTen.get(1);
        if (topTenValue != null) {
            LinkedList toptenList = (LinkedList)topTenValue.getField("toptenlist");
            if (toptenList.size() != 0) {
//                smallestToptenCount = ((Byte)((PdxInstance)toptenList.getLast()).getField("count")).intValue();
                smallestToptenCount = Integer.parseInt(((PdxInstance)toptenList.getLast()).getField("count").toString());
            }
        }

        Long keyTimestamp = 0L;
        String keyUuid = "";
        String keyRoute = "";
        Integer keyCount = 0;
        Boolean incremental = true;

        System.out.println("new events, events.size:" + events.size());
        try {
            for (AsyncEvent event : events) {


                Operation operation = event.getOperation();
                Integer countDiff = 0;

                if (operation.equals(Operation.PUTALL_CREATE)) {
                    countDiff = 1;
                }
                else if (operation.equals(Operation.EXPIRE_DESTROY)) {
                    countDiff = -1;
                }
                else {
                    System.out.println("unknown ooperation " + event.getOperation());
                    continue;
                }

                PdxInstance raw = (PdxInstance) event.getDeserializedValue();

                // get route from the key in JSON format
                String route = (String)raw.getField("route");
                Long newTimestamp = (Long)raw.getField("timestamp");

                Integer originalCount = 0 ;
                Integer newCount = 0;
                Long originalTimestamp = 0L;

                PdxInstance originCountValue = (PdxInstance)regionCount.get(route);

                if(originCountValue==null){
                    newCount = 1;
                }
                else
                {
//                    originalCount = ((Byte)originCountValue.getField("route_count")).intValue();
                    originalCount = Integer.parseInt(originCountValue.getField("route_count").toString());
                    originalTimestamp = (Long)originCountValue.getField("timestamp");
                    newCount = originalCount + countDiff;
                }

//                processRegionCount(regionCount, route, originalCount, originalTimestamp, newCount, newTimestamp);
                processor.processRegionCount(route, originalCount, originalTimestamp, newCount, newTimestamp);

//                processRegionTop(regionTop, route, originalCount, originalTimestamp, newCount, newTimestamp);
                processor.processRegionTop(route, originalCount, originalTimestamp, newCount, newTimestamp);

                // Check whether need to refresh topten
                if (newCount > originalCount) {

                    if (newCount >= smallestToptenCount) {
                        isProcessTopTen = Boolean.logicalOr(isProcessTopTen, Boolean.TRUE);

                        if (keyRoute.isEmpty()) {
                            keyTimestamp = (Long)raw.getField("timestamp");
                            keyUuid = (String)raw.getField("uuid");
                            keyRoute = route;
                            keyCount = newCount;
                            incremental = true;
                        }
                    }
                }
//                else if (newCount < originalCount){
//                    // TODO waiting for expiration destroy opertaion to be published in async event
//                    // https://issues.apache.org/jira/browse/GEODE-1209
//                    if (originalCount >= smallestToptenCount) {
//                        isProcessTopTen = Boolean.logicalOr(isProcessTopTen, Boolean.TRUE);
//                    }
//                }
            }

            if (isProcessTopTen) {
//                processRegionTopTen(regionTop, regionTopTen, keyRoute, keyUuid, keyCount, keyTimestamp);
                processor.processRegionTopTen(keyRoute, keyUuid, keyCount, keyTimestamp, incremental);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }



    @Override
    public void close() {
        // TODO Auto-generated method stub

    }

    @Override
    public void init(Properties arg0) {
        // TODO Auto-generated method stub

    }
}