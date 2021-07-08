package com.amazon.elasticsearch.replication.seqno

import org.apache.logging.log4j.LogManager
import org.elasticsearch.ResourceNotFoundException
import org.elasticsearch.common.component.AbstractLifecycleComponent
import org.elasticsearch.common.inject.Singleton
import org.elasticsearch.core.internal.io.IOUtils
import org.elasticsearch.index.engine.Engine
import org.elasticsearch.index.shard.IndexShard
import org.elasticsearch.index.translog.Translog
import java.io.Closeable

@Singleton
class RemoteClusterTranslogService : AbstractLifecycleComponent(){
    companion object {
        private val log = LogManager.getLogger(RemoteClusterTranslogService::class.java)
        private const val SOURCE_NAME = "os_plugin_replication"
    }

    override fun doStart() {
    }

    override fun doStop() {
    }

    override fun doClose() {
    }
    
    public fun getHistoryOfOperations(indexShard: IndexShard, startSeqNo: Long, toSeqNo: Long): List<Translog.Operation> {
        if(!indexShard.hasCompleteHistoryOperations(SOURCE_NAME, Engine.HistorySource.TRANSLOG, startSeqNo)) {
            log.debug("Doesn't have history of operations starting from $startSeqNo")
            throw ResourceNotFoundException("$indexShard doesn't contain ops starting from $startSeqNo " +
                    "with source ${Engine.HistorySource.TRANSLOG.name}")
        }
        log.trace("Fetching translog snapshot for $indexShard - from $startSeqNo to $toSeqNo")
        val snapshot = indexShard.getHistoryOperations(SOURCE_NAME, Engine.HistorySource.TRANSLOG, startSeqNo, toSeqNo)

        // Total ops to be fetched (both toSeqNo and startSeqNo are inclusive)
        val opsSize = toSeqNo - startSeqNo + 1
        val ops = ArrayList<Translog.Operation>(opsSize.toInt())

        // Filter and sort specific ops from the obtained history
        var filteredOpsFromTranslog = 0
        snapshot.use {
            var op  = snapshot.next()
            while(op != null) {
                if(op.seqNo() in startSeqNo..toSeqNo) {
                    ops.add(op)
                    filteredOpsFromTranslog++
                }
                op = snapshot.next()
            }
        }
        assert(filteredOpsFromTranslog == opsSize.toInt()) {"Missing operations while fetching from translog"}

        val sortedOps = ArrayList<Translog.Operation>(opsSize.toInt())
        sortedOps.addAll(ops)
        for(ele in ops) {
            sortedOps[(ele.seqNo() - startSeqNo).toInt()] = ele
        }

        log.debug("Starting seqno after sorting ${sortedOps[0].seqNo()} and ending seqno ${sortedOps[ops.size-1].seqNo()}")
        return sortedOps.subList(0, ops.size.coerceAtMost((opsSize).toInt()))
    }
}