/**
 * Distribution License:
 * JSword is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License, version 2.1 as published by
 * the Free Software Foundation. This program is distributed in the hope
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The License is available on the internet at:
 *       http://www.gnu.org/copyleft/lgpl.html
 * or by writing to:
 *      Free Software Foundation, Inc.
 *      59 Temple Place - Suite 330
 *      Boston, MA 02111-1307, USA
 *
 * Copyright: 2005
 *     The copyright to this program is held by it's authors.
 *
 * ID: $Id$
 */
package org.crosswire.jsword.book.sword;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;

import org.crosswire.common.activate.Activator;
import org.crosswire.common.activate.Lock;
import org.crosswire.common.util.FileUtil;
import org.crosswire.common.util.Logger;
import org.crosswire.common.util.NetUtil;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;

/**
 * Both Books and Commentaries seem to use the same format so this class
 * abstracts out the similarities.
 *
 * @see gnu.lgpl.License for license details.
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 */
public class RawBackend extends AbstractBackend
{
    /**
     * Simple ctor
     */
    public RawBackend(SwordBookMetaData sbmd, int datasize) throws BookException
    {
        super(sbmd);
        this.datasize = datasize;

        assert (datasize == 2 || datasize == 4);

        URI path = getExpandedDataPath();

        URI otPath = NetUtil.lengthenURI(path, File.separator + SwordConstants.FILE_OT);
        txtFile[SwordConstants.TESTAMENT_OLD] = new File(otPath.getPath());
        idxFile[SwordConstants.TESTAMENT_OLD] = new File(otPath.getPath() + SwordConstants.EXTENSION_VSS);

        URI ntPath = NetUtil.lengthenURI(path, File.separator + SwordConstants.FILE_NT);
        txtFile[SwordConstants.TESTAMENT_NEW] = new File(ntPath.getPath());
        idxFile[SwordConstants.TESTAMENT_NEW] = new File(ntPath.getPath() + SwordConstants.EXTENSION_VSS);

        // It is an error to be neither OT nor NT
        if (!txtFile[SwordConstants.TESTAMENT_OLD].canRead() && !txtFile[SwordConstants.TESTAMENT_NEW].canRead())
        {
            throw new BookException(Msg.MISSING_FILE, new Object[] { path });
        }
    }

    /* (non-Javadoc)
     * @see org.crosswire.common.activate.Activatable#activate(org.crosswire.common.activate.Lock)
     */
    public final void activate(Lock lock)
    {
        String fileMode = isWritable() ? FileUtil.MODE_WRITE : FileUtil.MODE_READ;
 
        if (idxFile[SwordConstants.TESTAMENT_OLD].canRead())
        {
            try
            {
                idxRaf[SwordConstants.TESTAMENT_OLD] = new RandomAccessFile(idxFile[SwordConstants.TESTAMENT_OLD], fileMode);
                txtRaf[SwordConstants.TESTAMENT_OLD] = new RandomAccessFile(txtFile[SwordConstants.TESTAMENT_OLD], fileMode);
            }
            catch (FileNotFoundException ex)
            {
                assert false : ex;
                log.error("Could not open OT", ex); //$NON-NLS-1$
                idxRaf[SwordConstants.TESTAMENT_OLD] = null;
                txtRaf[SwordConstants.TESTAMENT_OLD] = null;
            }
        }

        if (idxFile[SwordConstants.TESTAMENT_NEW].canRead())
        {
            try
            {
                idxRaf[SwordConstants.TESTAMENT_NEW] = new RandomAccessFile(idxFile[SwordConstants.TESTAMENT_NEW], fileMode);
                txtRaf[SwordConstants.TESTAMENT_NEW] = new RandomAccessFile(txtFile[SwordConstants.TESTAMENT_NEW], fileMode);
            }
            catch (FileNotFoundException ex)
            {
                assert false : ex;
                log.error("Could not open NT", ex); //$NON-NLS-1$
                idxRaf[SwordConstants.TESTAMENT_NEW] = null;
                txtRaf[SwordConstants.TESTAMENT_NEW] = null;
            }
        }

        active = true;
    }

    /* (non-Javadoc)
     * @see org.crosswire.common.activate.Activatable#deactivate(org.crosswire.common.activate.Lock)
     */
    public final void deactivate(Lock lock)
    {
        try
        {
            idxRaf[SwordConstants.TESTAMENT_OLD].close();
            txtRaf[SwordConstants.TESTAMENT_OLD].close();

            idxRaf[SwordConstants.TESTAMENT_NEW].close();
            txtRaf[SwordConstants.TESTAMENT_NEW].close();
        }
        catch (IOException ex)
        {
            log.error("Failed to close files", ex); //$NON-NLS-1$
        }
        finally
        {
            idxRaf[SwordConstants.TESTAMENT_OLD] = null;
            txtRaf[SwordConstants.TESTAMENT_OLD] = null;

            idxRaf[SwordConstants.TESTAMENT_NEW] = null;
            txtRaf[SwordConstants.TESTAMENT_NEW] = null;
        }

        active = false;
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.sword.AbstractBackend#getRawText(org.crosswire.jsword.passage.Key, java.lang.String)
     */
    /* @Override */
    public String getRawText(Key key) throws BookException
    {
        checkActive();
        String charset = getBookMetaData().getBookCharset();

        Verse verse = KeyUtil.getVerse(key);

        try
        {
            int testament = SwordConstants.getTestament(verse);
            long index = SwordConstants.getIndex(verse);

            // If this is a single testament Bible, return nothing.
            if (idxRaf[testament] == null)
            {
                return ""; //$NON-NLS-1$
            }

            int entrysize = datasize + OFFSETSIZE;

            // Read the next entry size bytes.
            byte[] read = SwordUtil.readRAF(idxRaf[testament], index * entrysize, entrysize);
            if (read == null || read.length == 0)
            {
                return ""; //$NON-NLS-1$
            }

            // The data is little endian - extract the start and size
            long start = SwordUtil.decodeLittleEndian32(read, 0);
            int size = -1;
            switch (datasize)
            {
            case 2:
                size = SwordUtil.decodeLittleEndian16(read, 4);
                break;
            case 4:
                size = SwordUtil.decodeLittleEndian32(read, 4);
                break;
            default:
                assert false : datasize;
            }

            if (size < 0)
            {
                log.error("In " + getBookMetaData().getInitials() + ": Verse " + verse.getName() + " has a bad index size of " + size); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }

            // Read from the data file.
            // I wonder if it would be safe to do a readLine() from here.
            // Probably be safer not to risk it since we know how long it is.
            byte[] data = SwordUtil.readRAF(txtRaf[testament], start, size);

            decipher(data);

            return SwordUtil.decode(key.getName(), data, charset);
        }
        catch (IOException ex)
        {
            throw new BookException(UserMsg.READ_FAIL, ex, new Object[] { verse.getName() });
        }
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.sword.AbstractBackend#readIndex()
     */
    /* @Override */
    public Key readIndex()
    {
        // PENDING(joe): refactor to get rid of this
        return null;
    }

    /**
     * Helper method so we can quickly activate ourselves on access
     */
    protected final void checkActive()
    {
        if (!active)
        {
            Activator.activate(this);
        }
    }

    /* (non-Javadoc)
     * @see org.crosswire.jsword.book.sword.AbstractBackend#isWritable()
     */
    public boolean isWritable()
    {
        // For the module to be writable either the old testament or the new testament needs to be present
        // (i.e. readable) and both the index and the data files need to be readable
        if (idxFile[SwordConstants.TESTAMENT_OLD].canRead()
            && (idxFile[SwordConstants.TESTAMENT_OLD].canWrite() || !txtFile[SwordConstants.TESTAMENT_OLD].canWrite()))
        {
            return false;
        }
        if (idxFile[SwordConstants.TESTAMENT_NEW].canRead()
            && (idxFile[SwordConstants.TESTAMENT_NEW].canWrite() || !txtFile[SwordConstants.TESTAMENT_NEW].canWrite()))
        {
            return false;
        }
        return idxFile[SwordConstants.TESTAMENT_OLD].canRead() || idxFile[SwordConstants.TESTAMENT_NEW].canRead();
    }

    public void create(String path)
    {
        idxFile[SwordConstants.TESTAMENT_OLD] = new File(path + File.separator + SwordConstants.FILE_OT + SwordConstants.EXTENSION_VSS);
        txtFile[SwordConstants.TESTAMENT_OLD] = new File(path + File.separator + SwordConstants.FILE_OT);

        idxFile[SwordConstants.TESTAMENT_NEW] = new File(path + File.separator + SwordConstants.FILE_NT + SwordConstants.EXTENSION_VSS);
        txtFile[SwordConstants.TESTAMENT_NEW] = new File(path + File.separator + SwordConstants.FILE_NT);
    }

    /**
     * Are we active
     */
    private boolean active;

    /**
     * The log stream
     */
    private static final Logger log = Logger.getLogger(RawBackend.class);

    /**
     * The array of index files
     */
    private RandomAccessFile[] idxRaf = new RandomAccessFile[3];

    /**
     * The array of data files
     */
    private RandomAccessFile[] txtRaf = new RandomAccessFile[3];

    /**
     * The array of index random access files
     */
    private File[] idxFile = new File[3];

    /**
     * The array of data random access files
     */
    private File[] txtFile = new File[3];

    /**
     * How many bytes in the offset pointers in the index
     */
    private static final int OFFSETSIZE = 4;

    /**
     * How many bytes in the size count in the index
     */
    private int datasize = -1;
}
