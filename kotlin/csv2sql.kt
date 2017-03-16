#!/usr/bin/env kotlin-script.sh
package csv2sql

import okSource
import okio.BufferedSource
import java.io.File

fun main(args: Array<String>) {
    val file = File(args[0])

    generateSql(file.okSource())
}

val start = """
# Generated by a kotlin script from a CSV export
INSERT INTO `market` (created_at,updated_at,deleted_at,version,name,customer_id,market_group,ecr_id,field_worker,city,street,zipcode,ceo,director,dispatcher,phone)
VALUES
"""
val pattern = """
('2016-12-09 16:27:53','2016-12-09 16:27:53',NULL,0,'%s',%s,%s,'%s','%s','%s','%s','%s','%s','%s','%s','%s'),
"""

fun generateSql(okSource: BufferedSource) {
    println(start)
    okSource.readUtf8Line()
    while (!okSource.exhausted()) {
        val line = okSource.readUtf8Line()
        val data = line.split(";").toTypedArray()
        if (data.size < 12) {
            continue
        }
        val output = String.format(pattern, *data).trim()
        println(output)
    }

    println(";")

}