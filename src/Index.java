

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class Index {

	// Term id -> (position in index file, doc frequency) dictionary
	private static Map<Integer, Pair<Long, Integer>> postingDict 
		= new TreeMap<Integer, Pair<Long, Integer>>();
	// Doc name -> doc id dictionary
	private static Map<String, Integer> docDict
		= new TreeMap<String, Integer>();
	// Term -> term id dictionary
	private static Map<String, Integer> termDict
		= new TreeMap<String, Integer>();
	// Block queue
	private static LinkedList<File> blockQueue
		= new LinkedList<File>();
	
	// Total file counter
	private static int totalFileCount = 0;
	// Document counter
	private static int docIdCounter = 0;
	// Term counter
	private static int wordIdCounter = 0;
	// Index
	private static BaseIndex index = null;
	
	//added by Lee
	private static int totalFreq = 0;

	
	/* 
	 * Write a posting list to the given file 
	 * You should record the file position of this posting list
	 * so that you can read it back during retrieval
	 * 
	 * */
	private static void writePosting(FileChannel fc, PostingList posting)
			throws IOException {
		/*
		 * TODO: Your code here
		 *	 
		 */
		
		index.writePosting(fc, posting);
	}
	

	 /**
     * Pop next element if there is one, otherwise return null
     * @param iter an iterator that contains integers
     * @return next element or null
     */
    private static Integer popNextOrNull(Iterator<Integer> iter) {
        if (iter.hasNext()) {
            return iter.next();
        } else {
            return null;
        }
    }
	
    //helper function, for managing postingDict stuff in merging part
    /**
     * Add termid and frequencyinto postingDict
     * the pointer will be calculate here
     * @param 	termId ID of the term adding
     * 			freq frequency of document containing that term
     */
    private static void addTermAndFreqToPostingDict(int termId, int freq){
    	if(blockQueue.size() != 0)
    		return;
    	System.out.println("termID:"+termId+" added with "+freq+"frequency");
    	Long byteOffset = (long) (totalFreq * 4);
    	Pair<Long, Integer> temp = new Pair<Long, Integer>(byteOffset, freq);
    	postingDict.put(termId, temp);
    	totalFreq += 2 + freq;
    }
   
	
	/**
	 * Main method to start the indexing process.
	 * @param method		:Indexing method. "Basic" by default, but extra credit will be given for those
	 * 			who can implement variable byte (VB) or Gamma index compression algorithm
	 * @param dataDirname	:relative path to the dataset root directory. E.g. "./datasets/small"
	 * @param outputDirname	:relative path to the output directory to store index. You must not assume
	 * 			that this directory exist. If it does, you must clear out the content before indexing.
	 */
	public static int runIndexer(String method, String dataDirname, String outputDirname) throws IOException 
	{
		/* Get index */
		String className = method + "Index";
		try {
			Class<?> indexClass = Class.forName(className);
			index = (BaseIndex) indexClass.newInstance();
		} catch (Exception e) {
			System.err
					.println("Index method must be \"Basic\", \"VB\", or \"Gamma\"");
			throw new RuntimeException(e);
		}
		
		/* Get root directory */
		File rootdir = new File(dataDirname);
		if (!rootdir.exists() || !rootdir.isDirectory()) {
			System.err.println("Invalid data directory: " + dataDirname);
			return -1;
		}
		
		   
		/* Get output directory*/
		File outdir = new File(outputDirname);
		if (outdir.exists() && !outdir.isDirectory()) {
			System.err.println("Invalid output directory: " + outputDirname);
			return -1;
		}
		
		/*	TODO: delete all the files/sub folder under outdir
		 *  DONE by Earth
		 */
		
		try {
			
			for (File dir: outdir.listFiles()) {
				if(dir.exists()) {
					dir.delete();
				}
			}
		} catch (Exception e) {
			// TODO: handle exception
			System.out.println("error while trying to delete old files");
		}
			
		if (!outdir.exists()) {
			if (!outdir.mkdirs()) {
				System.err.println("Create output directory failure");
				return -1;
			}
		}
		
		
		
		
		/* BSBI indexing algorithm */
		File[] dirlist = rootdir.listFiles();

		/* For each block */
		for (File block : dirlist) {
			File blockFile = new File(outputDirname, block.getName());
			//System.out.println("Processing block "+block.getName());
			blockQueue.add(blockFile);

			File blockDir = new File(dataDirname, block.getName());
			File[] filelist = blockDir.listFiles();
			
			//posting Map for writing into a corpus
			//<termId, Posting>
			Map<Integer,PostingList> blockPostingLists = new TreeMap<Integer,PostingList>();
			
			/* For each file */
			for (File file : filelist) {
				++totalFileCount;
				String fileName = block.getName() + "/" + file.getName();
				
				 // use pre-increment to ensure docID > 0
                int docId = ++docIdCounter;
                docDict.put(fileName, docId);
				
				BufferedReader reader = new BufferedReader(new FileReader(file));
				String line;
				while ((line = reader.readLine()) != null) {
					String[] tokens = line.trim().split("\\s+");
					for (String token : tokens) {
						/*
						 * TODO: Your code here
						 *       For each term, build up a list of
						 *       documents in which the term occurs
						 */
						if(termDict.containsKey(token)){
							int termId = termDict.get(token);
							if(!blockPostingLists.containsKey(termId)){
								List<Integer> tempList = new ArrayList<Integer>();
								tempList.add(docId);
								blockPostingLists.put(termId,new PostingList(termId, tempList));
							}
							else if(!blockPostingLists.get(termId).getList().contains(docId))
								blockPostingLists.get(termId).addDocId(docId);
						}
						else{
							termDict.put(token, ++wordIdCounter);
							List<Integer> tempList = new ArrayList<Integer>();
							tempList.add(docId);
							blockPostingLists.put(wordIdCounter,new PostingList(wordIdCounter,tempList));
						}
					}
				}
				reader.close();
			}

			/* Sort and output */
			if (!blockFile.createNewFile()) {
				System.err.println("Create new block failure.");
				return -1;
			}
			
			RandomAccessFile bfc = new RandomAccessFile(blockFile, "rw");
			
			/*
			 * TODO: Your code here
			 *       Write all posting lists for all terms to file (bfc) 
			 */
			
			FileChannel channel = bfc.getChannel();
			
			for(PostingList posting:blockPostingLists.values()){
				writePosting(channel, posting);
			}
			
			bfc.close();
		}

		/* Required: output total number of files. */
		//System.out.println("Total Files Indexed: "+totalFileCount);

		/* Merge blocks */
		while (true) {
			if (blockQueue.size() <= 1)
				break;

			File b1 = blockQueue.removeFirst();
			File b2 = blockQueue.removeFirst();
			
			File combfile = new File(outputDirname, b1.getName() + "+" + b2.getName());
			if (!combfile.createNewFile()) {
				System.err.println("Create new block failure.");
				return -1;
			}

			RandomAccessFile bf1 = new RandomAccessFile(b1, "r");
			RandomAccessFile bf2 = new RandomAccessFile(b2, "r");
			RandomAccessFile mf = new RandomAccessFile(combfile, "rw");
			 
			/*
			 * TODO: Your code here
			 *       Combine blocks bf1 and bf2 into our combined file, mf
			 *       You will want to consider in what order to merge
			 *       the two blocks (based on term ID, perhaps?).
			 *       
			 */
			
			FileChannel mfc = mf.getChannel();
			
			System.out.println("file working: "+combfile.getName());
			long file1size = bf1.length();
			long file2size = bf2.length();
			int file1termId = bf1.readInt();
			int file2termId = bf2.readInt();
			while(bf1.getFilePointer() < file1size && bf2.getFilePointer() < file2size){
				if(file1termId < file2termId){
					int termFreq = bf1.readInt();
					ByteBuffer termBuffer = ByteBuffer.allocate((2+termFreq)*4);
					termBuffer.putInt(file1termId);
					termBuffer.putInt(termFreq);
					for(int i=0;i<termFreq;i++){
						int termDocId = bf1.readInt();
						termBuffer.putInt(termDocId);
					}
					addTermAndFreqToPostingDict(file1termId, termFreq);
					termBuffer.flip();
					mfc.write(termBuffer);
					termBuffer.clear();
					if(bf1.getFilePointer() >= file1size)
						break;
					file1termId = bf1.readInt();
				}
				else if(file1termId > file2termId){
					int termFreq = bf2.readInt();
					ByteBuffer termBuffer = ByteBuffer.allocate((2+termFreq)*4);
					termBuffer.putInt(file2termId);
					termBuffer.putInt(termFreq);
					for(int i=0;i<termFreq;i++){
						int termDocId = bf2.readInt();
						termBuffer.putInt(termDocId);
					}
					addTermAndFreqToPostingDict(file2termId, termFreq);
					termBuffer.flip();
					mfc.write(termBuffer);
					termBuffer.clear();
					if(bf2.getFilePointer() >= file2size)
						break;
					file2termId = bf2.readInt();
				}
				else{ //case: same term ID
					int termFreq1 = bf1.readInt();
					int termFreq2 = bf2.readInt();
					Set<Integer> termdocIds = new TreeSet<Integer>();
					//merge doc ID
					while(termFreq1-- > 0){
						termdocIds.add(bf1.readInt());
					}
					while(termFreq2-- > 0){
						termdocIds.add(bf2.readInt());
					}
					addTermAndFreqToPostingDict(file1termId, termdocIds.size());
					ByteBuffer termBuffer = ByteBuffer.allocate((2+termdocIds.size())*4);
					termBuffer.putInt(file2termId);
					termBuffer.putInt(termdocIds.size());
					for(Integer docId: termdocIds){
						termBuffer.putInt(docId);
					}
					termBuffer.flip();
					mfc.write(termBuffer);
					termBuffer.clear();
					if(bf1.getFilePointer() >= file1size && bf2.getFilePointer() >= file2size)
						break;
					else if(bf1.getFilePointer() >= file1size){
						file2termId = bf2.readInt();
						break;
					}
					else if(bf2.getFilePointer() >= file2size){
						file1termId = bf1.readInt();
						break;
					}
					file1termId = bf1.readInt();
					file2termId = bf2.readInt();
				}
			}
			while(bf1.getFilePointer() < file1size){
				int termFreq = bf1.readInt();
				ByteBuffer termBuffer = ByteBuffer.allocate((2+termFreq)*4);
				termBuffer.putInt(file1termId);
				termBuffer.putInt(termFreq);
				for(int i=0;i<termFreq;i++){
					int termDocId = bf1.readInt();
					termBuffer.putInt(termDocId);
				}
				addTermAndFreqToPostingDict(file1termId, termFreq);
				termBuffer.flip();
				mfc.write(termBuffer);
				termBuffer.clear();
				if(bf1.getFilePointer() >= file1size)
					break;
				file1termId = bf1.readInt();
			}
			while(bf2.getFilePointer() < file2size){
				int termFreq = bf2.readInt();
				ByteBuffer termBuffer = ByteBuffer.allocate((2+termFreq)*4);
				termBuffer.putInt(file2termId);
				termBuffer.putInt(termFreq);
				for(int i=0;i<termFreq;i++){
					int termDocId = bf2.readInt();
					termBuffer.putInt(termDocId);
				}
				addTermAndFreqToPostingDict(file2termId, termFreq);
				termBuffer.flip();
				mfc.write(termBuffer);
				termBuffer.clear();
				if(bf2.getFilePointer() >= file2size)
					break;
				file2termId = bf2.readInt();
			}

			
			bf1.close();
			bf2.close();
			mf.close();
			b1.delete();
			b2.delete();
			blockQueue.add(combfile);
		}

		/* Dump constructed index back into file system */
		File indexFile = blockQueue.removeFirst();
		indexFile.renameTo(new File(outputDirname, "corpus.index"));

		BufferedWriter termWriter = new BufferedWriter(new FileWriter(new File(
				outputDirname, "term.dict")));
		for (String term : termDict.keySet()) {
			termWriter.write(term + "\t" + termDict.get(term) + "\n");
		}
		termWriter.close();

		BufferedWriter docWriter = new BufferedWriter(new FileWriter(new File(
				outputDirname, "doc.dict")));
		for (String doc : docDict.keySet()) {
			docWriter.write(doc + "\t" + docDict.get(doc) + "\n");
		}
		docWriter.close();

		BufferedWriter postWriter = new BufferedWriter(new FileWriter(new File(
				outputDirname, "posting.dict")));
		for (Integer termId : postingDict.keySet()) {
			postWriter.write(termId + "\t" + postingDict.get(termId).getFirst()
					+ "\t" + postingDict.get(termId).getSecond() + "\n");
		}
		postWriter.close();
		
		return totalFileCount;
	}

	public static void main(String[] args) throws IOException {
		/* Parse command line */
		if (args.length != 3) {
			System.err
					.println("Usage: java Index [Basic|VB|Gamma] data_dir output_dir");
			return;
		}

		/* Get index */
		String className = "";
		try {
			className = args[0];
		} catch (Exception e) {
			System.err
					.println("Index method must be \"Basic\", \"VB\", or \"Gamma\"");
			throw new RuntimeException(e);
		}

		/* Get root directory */
		String root = args[1];
		

		/* Get output directory */
		String output = args[2];
		runIndexer(className, root, output);
	}

}
