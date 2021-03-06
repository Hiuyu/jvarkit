/*
The MIT License (MIT)

Copyright (c) 2014 Pierre Lindenbaum

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


History:
* 2014 creation

*/
package com.github.lindenb.jvarkit.tools.bamindexnames;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Comparator;

import com.github.lindenb.jvarkit.util.picard.AbstractDataCodec;
import com.github.lindenb.jvarkit.util.picard.SAMSequenceDictionaryProgress;

import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.SortingCollection;

public class BamIndexReadNames
	extends AbstractBamIndexReadNames
	{
	private BamIndexReadNames()
		{
		
		}
	
	
		
	private static class NameAndPosCodec extends AbstractDataCodec<NameAndPos>
		{
		@Override
		public NameAndPos decode(DataInputStream dis) throws IOException {
			NameAndPos nap=new NameAndPos();
			try
				{	
				nap.name=dis.readUTF();
				}
			catch(IOException err)
				{
				return null;
				}
			nap.tid=dis.readInt();
			nap.pos=dis.readInt();
			return nap;
			}
		@Override
		public void encode(DataOutputStream dos, NameAndPos nap)
				throws IOException {
			dos.writeUTF(nap.name);
			dos.writeInt(nap.tid);
			dos.writeInt(nap.pos);
			}
		@Override
		public AbstractDataCodec<NameAndPos> clone()
			{
			return new NameAndPosCodec();
			}
		}

	private static class NameAndPosComparator
		implements Comparator<NameAndPos>
		{
		@Override
		public int compare(NameAndPos o1, NameAndPos o2)
			{
			int i=o1.name.compareTo(o2.name);
			if(i!=0) return i;
			i=o1.tid-o2.tid;
			if(i!=0) return i;
			i=o1.pos-o2.pos;
			return i;
			}
		}

		private int maxRecordsInRAM=50000;

		
		private void indexBamFile(File bamFile) throws IOException
			{
			
			NameIndexDef indexDef=new NameIndexDef();

			SortingCollection<NameAndPos> sorting=null;
			info("Opening "+bamFile);
			SamReader sfr=SamReaderFactory.makeDefault().
					validationStringency(ValidationStringency.SILENT).
					open(bamFile);
			sorting=SortingCollection.newInstance(
					NameAndPos.class,
					new NameAndPosCodec() ,
					new NameAndPosComparator(),
					maxRecordsInRAM,
					getTmpDirectories()
					);
			sorting.setDestructiveIteration(true);
			if(sfr.getFileHeader().getSortOrder()!=SortOrder.coordinate)
				{
				throw new IOException("not SortOrder.coordinate "+sfr.getFileHeader().getSortOrder());
				}
			
			SAMRecordIterator iter=sfr.iterator();
			SAMSequenceDictionaryProgress progress=new SAMSequenceDictionaryProgress(sfr.getFileHeader().getSequenceDictionary());
			while(iter.hasNext())
				{
				SAMRecord rec=iter.next();
				progress.watch(rec);
				NameAndPos nap=new NameAndPos();
				nap.name=rec.getReadName();
				indexDef.maxNameLengt =Math.max(nap.name.length()+1, indexDef.maxNameLengt );
				nap.tid=rec.getReferenceIndex();
				nap.pos=rec.getAlignmentStart();
				indexDef.countReads++;
				sorting.add(nap);
				}
			progress.finish();
			iter.close();
			sfr.close();
			sorting.doneAdding();
			info("Done Adding. N="+indexDef.countReads);
			
			File indexFile=new File(bamFile.getParentFile(), bamFile.getName()+NAME_IDX_EXTENSION);
			
			info("Writing index "+indexFile);
			FileOutputStream raf=new FileOutputStream(indexFile);
			
			ByteBuffer byteBuff= ByteBuffer.allocate(8+4);
			byteBuff.putLong(indexDef.countReads);
			byteBuff.putInt(indexDef.maxNameLengt);
			raf.write(byteBuff.array());
			
			byteBuff= ByteBuffer.allocate( indexDef.maxNameLengt+4+4);
			CloseableIterator<NameAndPos> iter2=sorting.iterator();
			while(iter2.hasNext())
				{
				byteBuff.rewind();
				NameAndPos nap=iter2.next();
				for(int i=0;i< nap.name.length();++i)
					{
					byteBuff.put((byte)nap.name.charAt(i));
					}
				for(int i=nap.name.length();i< indexDef.maxNameLengt;++i)
					{
					byteBuff.put((byte)'\0');
					}
				byteBuff.putInt(nap.tid);
				byteBuff.putInt(nap.pos);
				raf.write(byteBuff.array());
				
				}
			raf.flush();
			raf.close();
			sorting.cleanup();
			}
		
	@Override
	protected String getOnlineDocUrl() {
		return DEFAULT_WIKI_PREFIX+"BamIndexReadNames";
		}
	
	@Override
	public String getProgramDescription() {
		return "Build a dictionary of read names to be searched with BamQueryReadNames";
		}

	
	
	@Override
	public void printOptions(java.io.PrintStream out)
		{
		super.printOptions(out);
		}
	
	@Override
	public int doWork(String[] args)
		{
		com.github.lindenb.jvarkit.util.cli.GetOpt opt=new com.github.lindenb.jvarkit.util.cli.GetOpt();
		int c;
		while((c=opt.getopt(args,getGetOptDefault()+""))!=-1)
			{
			switch(c)
				{
				default:
					{
					switch(handleOtherOptions(c, opt,args))
						{
						case EXIT_FAILURE: return -1;
						case EXIT_SUCCESS: return 0;
						default:break;
						}
					}
				}
			}
		
		
		try
			{
			if(opt.getOptInd()+1!=args.length)
				{
				info(getMessageBundle("illegal.number.of.arguments"));
				return -1;
				}
			File bamFile=new File(args[opt.getOptInd()]);
			if(bamFile.getParentFile()!=null)
				{
				addTmpDirectory(bamFile.getParentFile());
				}
			indexBamFile(bamFile);
			return 0;
			}
		catch(Exception err)
			{
			error(err);
			return -1;
			}
		finally
			{
			
			}
		}
	
	public static void main(String[] args) {
		new BamIndexReadNames().instanceMainWithExit(args);
	}
	
	}
