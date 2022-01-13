/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2022 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.cutlass.line.tcp.load;

import io.questdb.cairo.TableColumnMetadata;
import io.questdb.cairo.TableReaderMetadata;
import io.questdb.mp.SOCountDownLatch;
import io.questdb.std.IntLongPriorityQueue;
import io.questdb.std.ObjList;

import java.util.concurrent.TimeUnit;

import static io.questdb.cairo.ColumnType.*;

public class TableData {
    private final CharSequence tableName;
    private final ObjList<LineData> rows = new ObjList<>();
    private final IntLongPriorityQueue index = new IntLongPriorityQueue();
    private final SOCountDownLatch readyLatch = new SOCountDownLatch(1);

    public TableData(CharSequence tableName) {
        this.tableName = tableName;
    }

    public CharSequence getName() {
        return tableName;
    }

    public void notReady() {
        readyLatch.setCount(1);
    }

    public void ready() {
        readyLatch.countDown();
    }

    public boolean await(long seconds) {
        return readyLatch.await(TimeUnit.SECONDS.toNanos(seconds));
    }

    public synchronized int size() {
        return rows.size();
    }

    public synchronized void addLine(LineData line) {
        rows.add(line);
        index.add(rows.size() - 1, line.getTimestamp());
    }

    public synchronized CharSequence generateRows(TableReaderMetadata metadata) {
        final StringBuilder sb = new StringBuilder();
        final ObjList<CharSequence> columns = new ObjList<>();
        final ObjList<CharSequence> defaults = new ObjList<>();
        for (int i = 0, n = metadata.getColumnCount(); i < n; i++) {
            TableColumnMetadata colMetaData = metadata.getColumnQuick(i);
            CharSequence column = colMetaData.getName();
            columns.add(column);
            defaults.add(getDefaultValue((short) colMetaData.getType()));
            sb.append(column).append( i == n-1 ? "\n" : "\t");
        }
        for (int i = 0, n = rows.size(); i < n; i++) {
            sb.append(rows.get(index.popIndex()).getRow(columns, defaults));
            index.popValue();
        }
        return sb.toString();
    }

    private String getDefaultValue(short colType) {
        switch (colType) {
            case DOUBLE:
                return "NaN";
            case STRING:
            case SYMBOL:
            case TIMESTAMP:
                return "";
            default:
                throw new RuntimeException("Unexpected column type");
        }
    }

    @Override
    public synchronized String toString() {
        return "[" + tableName + ":" + rows + "]";
    }
}