package cn.com.warlock;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.PairFunction;

import scala.Tuple2;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;


public class JavaParseCsvTest {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: JavaParseCsvTest <inputFile> <outputFile>");
            System.exit(1);
        }

        String inputFile = args[0];
        String outputFile = args[1];

        SparkConf sparkConf = new SparkConf().setAppName("JavaParseCsvTest");
        JavaSparkContext jsc = new JavaSparkContext(sparkConf);

        JavaRDD<String> lines = jsc.textFile(inputFile);

        // csv读取
        JavaRDD<String> flatMapRdd = lines.flatMap(new FlatMapFunction<String, String>() {
            @Override
            public Iterable<String> call(String t) throws Exception {
                CSVReader csvReader = null;
                try {
                    csvReader = new CSVReader(new StringReader(t));
                    return Arrays.asList(csvReader.readNext());
                } finally {
                    if (csvReader != null) {
                        csvReader.close();
                    }
                }
            }
        });

        JavaPairRDD<String, Integer> javaPairRDD = flatMapRdd.mapToPair(new PairFunction<String, String, Integer>() {
            @Override
            public Tuple2<String, Integer> call(String s) {
                return new Tuple2<>(s, 1);
            }
        });

        // 统计元素出现次数
        JavaPairRDD<String, Integer> counts = javaPairRDD.reduceByKey(new Function2<Integer, Integer, Integer>() {
            @Override
            public Integer call(Integer i1, Integer i2) {
                return i1 + i2;
            }
        });

        // 使用csv格式写
        counts.mapPartitions(new FlatMapFunction<Iterator<Tuple2<String, Integer>>, String>() {
            @Override
            public Iterable<String> call(Iterator<Tuple2<String, Integer>> t) throws Exception {
                StringWriter stringWriter = new StringWriter();
                CSVWriter csvWriter = new CSVWriter(stringWriter);
                while (t.hasNext()) {
                    Tuple2 tuple2 = t.next();
                    csvWriter.writeNext(new String[] { tuple2._1().toString(), tuple2._2().toString() });
                }

                return Arrays.asList(stringWriter.toString());
            }
        }).saveAsTextFile(outputFile);

        jsc.close();
    }
}
