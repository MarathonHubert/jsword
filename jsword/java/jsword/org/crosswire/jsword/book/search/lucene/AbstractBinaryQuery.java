package org.crosswire.jsword.book.search.lucene;

/**
 * A binary token has a left token and right token.
 * 
 * <p><table border='1' cellPadding='3' cellSpacing='0'>
 * <tr><td bgColor='white' class='TableRowColor'><font size='-7'>
 *
 * Distribution Licence:<br />
 * JSword is free software; you can redistribute it
 * and/or modify it under the terms of the GNU General Public License,
 * version 2 as published by the Free Software Foundation.<br />
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.<br />
 * The License is available on the internet
 * <a href='http://www.gnu.org/copyleft/gpl.html'>here</a>, or by writing to:
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston,
 * MA 02111-1307, USA<br />
 * The copyright to this program is held by it's authors.
 * </font></td></tr></table>
 * @see gnu.gpl.Licence
 * @author DM Smith [ dmsmith555 at yahoo dot com]
 * @version $Id$
 */
public abstract class AbstractBinaryQuery implements Query
{

    /**
     * 
     */
    public AbstractBinaryQuery(Query theLeftToken, Query theRightToken)
    {
        leftToken = theLeftToken;
        rightToken = theRightToken;
    }

    /**
     * @return Returns the leftToken.
     */
    public Query getLeftToken()
    {
        return leftToken;
    }

    /**
     * @return Returns the rightToken.
     */
    public Query getRightToken()
    {
        return rightToken;
    }

    private Query leftToken;
    private Query rightToken;
}