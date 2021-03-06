package com.ctrip.xpipe.redis.console.service.impl;

import com.ctrip.xpipe.redis.console.dao.ShardDao;
import com.ctrip.xpipe.redis.console.exception.ServerException;
import com.ctrip.xpipe.redis.console.model.*;
import com.ctrip.xpipe.redis.console.notifier.ClusterMetaModifiedNotifier;
import com.ctrip.xpipe.redis.console.notifier.shard.ShardDeleteEvent;
import com.ctrip.xpipe.redis.console.notifier.shard.ShardEvent;
import com.ctrip.xpipe.redis.console.notifier.shard.ShardEventListener;
import com.ctrip.xpipe.redis.console.query.DalQuery;
import com.ctrip.xpipe.redis.console.service.*;
import com.ctrip.xpipe.redis.console.spring.ConsoleContextConfig;
import com.ctrip.xpipe.utils.ObjectUtils;
import com.ctrip.xpipe.utils.StringUtil;
import com.ctrip.xpipe.utils.VisibleForTesting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unidal.dal.jdbc.DalException;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

@Service
public class ShardServiceImpl extends AbstractConsoleService<ShardTblDao> implements ShardService {
	
	@Autowired
	private DcService dcService;
	@Autowired
	private ShardDao shardDao;
	@Autowired
	private ClusterMetaModifiedNotifier notifier;

	@Autowired
	private DcClusterShardService dcClusterShardService;

	@Autowired
	private SentinelService sentinelService;

	@Autowired
	private List<ShardEventListener> shardEventListeners;

	@Resource(name = ConsoleContextConfig.GLOBAL_EXECUTOR)
	private ExecutorService executors;
	
	@Override
	public ShardTbl find(final long shardId) {
		return queryHandler.handleQuery(new DalQuery<ShardTbl>() {
			@Override
			public ShardTbl doQuery() throws DalException {
				return dao.findByPK(shardId, ShardTblEntity.READSET_FULL);
			}
    	});
	}

	@Override
	public ShardTbl find(final String clusterName, final String shardName) {
		return queryHandler.handleQuery(new DalQuery<ShardTbl>() {
			@Override
			public ShardTbl doQuery() throws DalException {
				return dao.findShard(clusterName, shardName, ShardTblEntity.READSET_FULL);
			}
    	});
	}

	@Override
	public List<ShardTbl> findAllByClusterName(final String clusterName) {
		return queryHandler.handleQuery(new DalQuery<List<ShardTbl>>() {
			@Override
			public List<ShardTbl> doQuery() throws DalException {
				return dao.findAllByClusterName(clusterName, ShardTblEntity.READSET_FULL);
			}
    	});
	}

	@Override
	public List<ShardTbl> findAllShardNamesByClusterName(final String clusterName) {
		return queryHandler.handleQuery(new DalQuery<List<ShardTbl>>() {
			@Override
			public List<ShardTbl> doQuery() throws DalException {
				return dao.findAllByClusterName(clusterName, ShardTblEntity.READSET_NAME);
			}
    	});
	}

	@Override
	public synchronized ShardTbl createShard(final String clusterName, final ShardTbl shard,
											 final Map<Long, SetinelTbl> sentinels) {
		return queryHandler.handleQuery(new DalQuery<ShardTbl>() {
			@Override
			public ShardTbl doQuery() throws DalException {
				return shardDao.createShard(clusterName, shard, sentinels);
			}
    	});
	}

	@Override
	public synchronized ShardTbl findOrCreateShardIfNotExist(String clusterName, ShardTbl shard,
															 Map<Long, SetinelTbl> sentinels) {

		logger.info("[findOrCreateShardIfNotExist] Begin find or create shard: {}", shard);
		String monitorName = shard.getSetinelMonitorName();

		List<ShardTbl> shards = shardDao.queryAllShardsByClusterName(clusterName);

		Set<String> monitorNames = shardDao.queryAllShardMonitorNames();

		ShardTbl dupShardTbl = null;
		if(shards != null) {
			for (ShardTbl shardTbl : shards) {
				if (shardTbl.getShardName().equals(shard.getShardName())) {
					logger.info("[findOrCreateShardIfNotExist] Shard exist as: {} for input shard: {}",
							shardTbl, shard);
					dupShardTbl = shardTbl;
				}
			}
		}

		if(StringUtil.isEmpty(monitorName)) {
			return generateMonitorNameAndReturnShard(dupShardTbl, monitorNames, clusterName, shard, sentinels);

		} else {
			return compareMonitorNameAndReturnShard(dupShardTbl, monitorNames, clusterName, shard, sentinels);
		}
	}

	@Override
	public void deleteShard(final String clusterName, final String shardName) {
		final ShardTbl shard = queryHandler.handleQuery(new DalQuery<ShardTbl>() {
			@Override
			public ShardTbl doQuery() throws DalException {
				return dao.findShard(clusterName, shardName, ShardTblEntity.READSET_FULL);
			}
    	});

    	if(null != shard) {
    		// Call shard event
			Map<Long, SetinelTbl> sentinels = sentinelService.findByShard(shard.getId());
			ShardEvent shardEvent = createShardDeleteEvent(clusterName, shardName, shard, sentinels);

			try {
				shardDao.deleteShardsBatch(shard);
			} catch (Exception e) {
				throw new ServerException(e.getMessage());
			}

			shardEvent.onEvent();

    	}
    	
    	/** Notify meta server **/
		List<DcTbl> relatedDcs = dcService.findClusterRelatedDc(clusterName);
    	if(null != relatedDcs) {
    		for(DcTbl dc : relatedDcs) {
    			notifier.notifyClusterUpdate(dc.getDcName(), clusterName);

    		}
    	}
	}

	@VisibleForTesting
	protected ShardDeleteEvent createShardDeleteEvent(String clusterName, String shardName, ShardTbl shardTbl,
												Map<Long, SetinelTbl> sentinelTblMap) {

		String monitorName = shardTbl.getSetinelMonitorName();
		ShardDeleteEvent shardDeleteEvent = new ShardDeleteEvent(clusterName, shardName, executors);
		shardDeleteEvent.setShardMonitorName(monitorName);

		// Splicing sentinel address as "127.0.0.1:6379,127.0.0.2:6380"
		StringBuffer sb = new StringBuffer();
		for(SetinelTbl setinelTbl : sentinelTblMap.values()) {
			sb.append(setinelTbl.getSetinelAddress()).append(",");
		}
		sb.deleteCharAt(sb.length() - 1);

		shardDeleteEvent.setShardSentinels(sb.toString());
		shardEventListeners.forEach(shardEventListener -> shardDeleteEvent.addObserver(shardEventListener));
		return shardDeleteEvent;
	}

	private ShardTbl generateMonitorNameAndReturnShard(ShardTbl dupShardTbl, Set<String> monitorNames,
													   String clusterName, ShardTbl shard,
													   Map<Long, SetinelTbl> sentinels) {
		String monitorName = null;
		if(dupShardTbl == null) {
			monitorName = monitorNames.contains(shard.getShardName())
					? clusterName + "-" + shard.getShardName()
					: shard.getShardName();
			if(monitorNames.contains(monitorName)) {
				logger.error("[findOrCreateShardIfNotExist] monitor name duplicated with {} and {}",
						shard.getShardName(), monitorName);
				throw new IllegalStateException(String.format("Both %s and %s is assigned as sentinel monitor name",
						shard.getShardName(), monitorName));
			}
			shard.setSetinelMonitorName(monitorName);
			try {
				return shardDao.insertShard(clusterName, shard, sentinels);
			} catch (DalException e) {
				throw new IllegalStateException(e);
			}
		} else {
			return dupShardTbl;
		}
	}

	private ShardTbl compareMonitorNameAndReturnShard(ShardTbl dupShardTbl, Set<String> monitorNames,
													  String clusterName, ShardTbl shard,
													  Map<Long, SetinelTbl> sentinels) {

		String monitorName = shard.getSetinelMonitorName();
		if(dupShardTbl == null) {
			if(monitorNames.contains(monitorName)) {
				logger.error("[findOrCreateShardIfNotExist] monitor name by post already exist {}", monitorName);
				throw new IllegalArgumentException(String.format("Shard monitor name %s already exist",
						monitorName));
			} else {
				try {
					return shardDao.insertShard(clusterName,shard, sentinels);
				} catch (DalException e) {
					throw new IllegalStateException(e);
				}
			}
		} else {
			if(!ObjectUtils.equals(dupShardTbl.getSetinelMonitorName(), monitorName)) {
				logger.error("[findOrCreateShardIfNotExist] shard monitor name in-consist with previous, {} -> {}",
						monitorName, dupShardTbl.getSetinelMonitorName());
				throw new IllegalArgumentException(String.format("Post shard monitor name %s diff from previous %s",
						monitorName, dupShardTbl.getSetinelMonitorName()));
			}
			return dupShardTbl;
		}
	}

}
