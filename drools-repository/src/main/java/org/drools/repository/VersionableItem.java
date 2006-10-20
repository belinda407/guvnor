package org.drools.repository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;

import org.drools.repository.Item;
import org.drools.repository.util.VersionNumberGenerator;

public abstract class VersionableItem extends Item {

    /**
     * The name of the title property on the node type
     */
    public static final String TITLE_PROPERTY_NAME         = "drools:title";

    /**
     * The name of the description property on the rule node type
     */
    public static final String DESCRIPTION_PROPERTY_NAME   = "drools:description";

    /**
     * The name of the last modified property on the rule node type
     */
    public static final String LAST_MODIFIED_PROPERTY_NAME = "drools:lastModified";

    /**
     * The name of the last modified property on the rule node type
     */
    public static final String FORMAT_PROPERTY_NAME        = "drools:format";

    
    /** The name of the checkin/change comment for change tracking */
    public static final String CHECKIN_COMMENT              = "drools:checkinComment";
    
    public static final String VERSION_NUMBER_PROPERTY_NAME = "drools:versionNumber";
    
    /**
     * The name of the tag property on the rule node type
     */
    public static final String TAG_PROPERTY_NAME = "drools:categoryReference";
        
    
    /**
     * The possible formats for the format property of the node
     */
    public static final String RULE_FORMAT                 = "Rule";
    public static final String DSL_FORMAT                  = "DSL";
    public static final String RULE_PACKAGE_FORMAT         = "Rule Package";
    public static final String FUNCTION_FORMAT             = "Function";

    /** this is what is referred to when reading content from a versioned node */
    private Node               contentNode                 = null;

    /**
     * Sets this object's node attribute to the specified node
     * 
     * @param rulesRepository the RulesRepository object that this object is being created from
     * @param node the node in the repository that this item corresponds to
     */
    public VersionableItem(RulesRepository rulesRepository,
                           Node node) {
        super( rulesRepository,
               node );
    }

    /**
     * This will return true if the current entity is actually a
     * historical version (which means is effectively read only).
     */
    public boolean isHistoricalVersion() throws RepositoryException {
        return this.node.getPrimaryNodeType().getName().equals("nt:version") || node.getPrimaryNodeType().getName().equals( "nt:frozenNode" );
    }    
    
    /**
     * @return the predessor node of this node in the version history, or null if no predecessor version exists
     * @throws RulesRepositoryException
     */
    protected Node getPrecedingVersionNode() throws RulesRepositoryException {
        try {
            Node versionNode;
            if ( this.node.getPrimaryNodeType().getName().equals( "nt:version" ) ) {
                versionNode = this.node;
            } else {
                versionNode = this.node.getBaseVersion();
            }

            Property predecessorsProperty = versionNode.getProperty( "jcr:predecessors" );
            Value[] predecessorValues = predecessorsProperty.getValues();

            if ( predecessorValues.length > 0 ) {
                Node predecessorNode = this.node.getSession().getNodeByUUID( predecessorValues[0].getString() );

                //we don't want to return the root node - it isn't a true predecessor
                if ( predecessorNode.getName().equals( "jcr:rootVersion" ) ) {
                    return null;
                }

                return predecessorNode;
            }
        } catch ( PathNotFoundException e ) {
            //do nothing - this will happen if no predecessors exits
        } catch ( Exception e ) {
            log.error( "Caught exception",
                       e );
            throw new RulesRepositoryException( e );
        }
        return null;
    }

    /**
     * @return the successor node of this node in the version history
     * @throws RulesRepositoryException
     */
    protected Node getSucceedingVersionNode() throws RulesRepositoryException {
        try {
            Property successorsProperty = this.node.getProperty( "jcr:successors" );
            Value[] successorValues = successorsProperty.getValues();

            if ( successorValues.length > 0 ) {
                Node successorNode = this.node.getSession().getNodeByUUID( successorValues[0].getString() );
                return successorNode;
            }
        } catch ( PathNotFoundException e ) {
            //do nothing - this will happen if no successors exist
        } catch ( Exception e ) {
            log.error( "Caught exception",
                       e );
            throw new RulesRepositoryException( e );
        }
        return null;
    }

    /**
     * @return an Iterator over VersionableItem objects encapsulating each successor node of this 
     *         Item's node
     * @throws RulesRepositoryException
     */
    public ItemVersionIterator getSuccessorVersionsIterator() throws RulesRepositoryException {
        return new ItemVersionIterator( this,
                                        ItemVersionIterator.ITERATION_TYPE_SUCCESSOR );
    }

    /**
     * @return an Iterator over VersionableItem objects encapsulating each predecessor node of this 
     *         Item's node
     * @throws RulesRepositoryException
     */
    public ItemVersionIterator getPredecessorVersionsIterator() throws RulesRepositoryException {
        return new ItemVersionIterator( this,
                                        ItemVersionIterator.ITERATION_TYPE_PREDECESSOR );
    }

    /**
     * Clients of this method can cast the resulting object to the type of object they are 
     * calling the method on (e.g. 
     *         <pre>
     *           RuleItem item;
     *           ...
     *           RuleItem predcessor = (RuleItem) item.getPrecedingVersion();
     *         </pre>
     * @return a VersionableItem object encapsulating the predessor node of this node in the 
     *         version history, or null if no predecessor version exists
     * @throws RulesRepositoryException
     */
    public abstract VersionableItem getPrecedingVersion() throws RulesRepositoryException;

    /**
     * Clients of this method can cast the resulting object to the type of object they are 
     * calling the method on (e.g. 
     *         <pre>
     *           RuleItem item;
     *           ...
     *           RuleItem successor = (RuleItem) item.getSucceedingVersion();
     *         </pre>
     *         
     * @return a VersionableItem object encapsulating the successor node of this node in the 
     *         version history. 
     * @throws RulesRepositoryException
     */
    public abstract VersionableItem getSucceedingVersion() throws RulesRepositoryException;

    /** 
     * Gets the Title of the versionable node.  See the Dublin Core documentation for more
     * explanation: http://dublincore.org/documents/dces/
     * 
     * @return the title of the node this object encapsulates
     * @throws RulesRepositoryException
     */
    public String getTitle() throws RulesRepositoryException {
        try {
            Node theNode = getVersionContentNode();

            Property data = theNode.getProperty( TITLE_PROPERTY_NAME );
            return data.getValue().getString();
        } catch ( Exception e ) {
            log.error( "Caught Exception",
                       e );
            throw new RulesRepositoryException( e );
        }
    }

    /** 
     * Creates a new version of this object's node, updating the title content 
     * for the node.
     * <br>
     * See the Dublin Core documentation for more
     * explanation: http://dublincore.org/documents/dces/
     * 
     * @param title the new title for the node
     * @throws RulesRepositoryException
     */
    public void updateTitle(String title) throws RulesRepositoryException {
        try {
            checkIsUpdateable();

            node.checkout();
            node.setProperty( TITLE_PROPERTY_NAME,
                                 title );
            Calendar lastModified = Calendar.getInstance();
            this.node.setProperty( LAST_MODIFIED_PROPERTY_NAME,
                                   lastModified );

        } catch ( Exception e ) {
            log.error( "Caught Exception",
                       e );
            throw new RulesRepositoryException( e );
        }
    }


    /**
     * See the Dublin Core documentation for more
     * explanation: http://dublincore.org/documents/dces/
     * 
     * @return the description of this object's node.
     * @throws RulesRepositoryException
     */
    public String getDescription() throws RulesRepositoryException {
        try {
            
            Property data = getVersionContentNode().getProperty( DESCRIPTION_PROPERTY_NAME );
            return data.getValue().getString();
        } catch ( Exception e ) {
            log.error( "Caught Exception",
                       e );
            throw new RulesRepositoryException( e );
        }
    }
    
    /**
     * get this version number (default is incrementing integer, but you
     * can provide an implementation of VersionNumberGenerator if needed).
     */
    public String getVersionNumber() {
        try {
            if (getVersionContentNode().hasProperty( VERSION_NUMBER_PROPERTY_NAME )) {           
                return getVersionContentNode().getProperty( VERSION_NUMBER_PROPERTY_NAME ).getString();
            } else {
                return null;
            }
        } catch ( RepositoryException e ) {
            throw new RulesRepositoryException(e);
        }
    }

    /**
     * This will return the checkin comment for the latest revision.
     */
    public String getCheckinComment() throws RulesRepositoryException {
        try {            
            Property data = getVersionContentNode().getProperty( CHECKIN_COMMENT );
            return data.getValue().getString();
        } catch ( Exception e ) {
            log.error( "Caught Exception",
                       e );
            throw new RulesRepositoryException( e );
        }
    }    
    
    /**
     * @return the date the function node (this version) was last modified
     * @throws RulesRepositoryException
     */
    public Calendar getLastModified() throws RulesRepositoryException {
        try {

            Property lastModifiedProperty = getVersionContentNode().getProperty( LAST_MODIFIED_PROPERTY_NAME );
            return lastModifiedProperty.getDate();
        } catch ( Exception e ) {
            log.error( "Caught Exception",
                       e );
            throw new RulesRepositoryException( e );
        }
    }

    /**
     * Creates a new version of this object's node, updating the description content 
     * for the node.
     * <br>
     * See the Dublin Core documentation for more
     * explanation: http://dublincore.org/documents/dces/ 
     * 
     * @param newDescriptionContent the new description content for the rule
     * @throws RulesRepositoryException
     */
    public void updateDescription(String newDescriptionContent) throws RulesRepositoryException {
        try {
            this.node.checkout();

            this.node.setProperty( DESCRIPTION_PROPERTY_NAME,
                                   newDescriptionContent );

            Calendar lastModified = Calendar.getInstance();
            this.node.setProperty( LAST_MODIFIED_PROPERTY_NAME,
                                   lastModified );

        } catch ( Exception e ) {
            log.error( "Caught Exception",
                       e );
            throw new RulesRepositoryException( e );
        }
    }


    /**
     * See the Dublin Core documentation for more
     * explanation: http://dublincore.org/documents/dces/
     * 
     * @return the format of this object's node
     * @throws RulesRepositoryException
     */
    public String getFormat() throws RulesRepositoryException {
        try {
            Node theNode = getVersionContentNode();
            Property data = theNode.getProperty( FORMAT_PROPERTY_NAME );
            return data.getValue().getString();
        } catch ( Exception e ) {
            log.error( "Caught Exception",
                       e );
            throw new RulesRepositoryException( e );
        }
    }

    /**
     * When retrieving content, if we are dealing with a version in the history, 
     * we need to get the actual content node to retrieve values.
     * 
     */
    public Node getVersionContentNode() throws RepositoryException,
                                       PathNotFoundException {
        if ( this.contentNode == null ) {
            
            if ( this.node.getPrimaryNodeType().getName().equals( "nt:version" ) ) {
                contentNode = this.node.getNode( "jcr:frozenNode" );
            } else {
                contentNode = this.node;
            }
            
        }

        return contentNode;
    }
    
    /** 
     * Need to get the name from the content node, not the version node
     * if it is in fact a version ! 
     */
    public String getName() {
        try {
            return getVersionContentNode().getName();
        } catch ( RepositoryException e) {
            throw new RulesRepositoryException(e);
        }
    }

    /**
     * This will check out the node prior to editing.
     */
    public void checkout() {

        try {
            this.node.checkout();
        } catch ( UnsupportedRepositoryOperationException e ) {
            String message = "";
            try {
                message = "Error: Caught UnsupportedRepositoryOperationException when attempting to checkout rule: " + this.node.getName() + ". Are you sure your JCR repository supports versioning? ";
                log.error( message,
                           e );
            } catch ( RepositoryException e1 ) {
                log.error( "Caught Exception",
                           e );
                throw new RulesRepositoryException( e1 );
            }
            throw new RulesRepositoryException( message,
                                                e );
        } catch ( Exception e ) {
            log.error( "Caught Exception",
                       e );
            throw new RulesRepositoryException( e );
        }
    }
    
    /** 
     * This will save the content (if it hasn't been already) and 
     * then check it in to create a new version.
     * It will also set the last modified property.
     */
    public void checkin(String comment)  {
        try {
            this.node.setProperty( LAST_MODIFIED_PROPERTY_NAME, Calendar.getInstance() );
            this.node.setProperty( CHECKIN_COMMENT, comment);
            VersionNumberGenerator gen = rulesRepository.versionNumberGenerator;
            String nextVersion = gen.calculateNextVersion( getVersionNumber(), this);
            this.node.setProperty( VERSION_NUMBER_PROPERTY_NAME, nextVersion );
            this.node.getSession().save();        
            this.node.checkin();
        } catch (RepositoryException e) {
            throw new RulesRepositoryException("Unable to checkin.", e);
        }
    }

    /**
     * This will check to see if the node is the "head" and
     * so can be updated (you can't update historical nodes ).
     * @throws RulesRepositoryException if it is not allowed
     * (means a programming error !).
     */
    protected void checkIsUpdateable() {
        try {
            if(this.node.getPrimaryNodeType().getName().equals("nt:version")) {
                String message = "Error. Tags can only be added to the head version of a rule node";
                log.error(message);
                throw new RulesRepositoryException(message);
            }
        } catch ( RepositoryException e ) {
            throw new RulesRepositoryException(e);
        }
    }   
    
    /**
     * Adds the specified tag to this object's rule node. Tags are stored as nodes in a tag area of
     * the repository. If the specified tag does not already have a corresponding node, a node is 
     * created for it.
     *  
     * @param tag the tag to add to the rule. rules can have multiple tags
     * @throws RulesRepositoryException 
     */
    public void addCategory(String tag) throws RulesRepositoryException {
        try {
            //make sure this object's node is the head version
            checkIsUpdateable();                                       
            
            CategoryItem tagItem = this.rulesRepository.loadCategory(tag);
                                    
            //now set the tag property of the rule
            Property tagReferenceProperty;
            int i = 0;
            Value[] newTagValues = null;
            try {
                tagReferenceProperty = this.node.getProperty(TAG_PROPERTY_NAME);
                Value[] oldTagValues = tagReferenceProperty.getValues();
                
                //first, make sure this tag wasn't already there. while we're at it, lets copy the array
                newTagValues = new Value[oldTagValues.length + 1];                
                for(i=0; i<oldTagValues.length; i++) {
                    if(oldTagValues[i].getString().equals(tag)) {
                        log.info("tag '" + tag + "' already existed for rule node: " + this.node.getName());
                        return;
                    }
                    newTagValues[i] = oldTagValues[i];
                }
            }
            catch(PathNotFoundException e) {
                //the property doesn't exist yet, so create it in the finally block
                newTagValues = new Value[1];                 
            }
            finally {   
                if(newTagValues != null) {
                    newTagValues[i] = this.node.getSession().getValueFactory().createValue(tagItem.getNode());
                    this.node.checkout();
                    this.node.setProperty(TAG_PROPERTY_NAME, newTagValues);
                }
                else {
                    log.error("reached expected path of execution when adding tag '" + tag + "' to ruleNode: " + this.node.getName());
                }
            }
        }
        catch(Exception e) {
            log.error("Caught exception", e);
            throw new RulesRepositoryException(e);
        }
    }   
    
    /**
     * Gets a list of CategoryItem objects for this object's rule node.
     * 
     * @return a list of TagItem objects for each tag on the rule. If there are no tags, an empty list. 
     * @throws RulesRepositoryException
     */
    public List getCategories() throws RulesRepositoryException {
        try {                            
            Node ruleNode = getVersionContentNode();
            
            List returnList = new ArrayList();
            try {
                Property tagReferenceProperty = ruleNode.getProperty(TAG_PROPERTY_NAME);
                Value[] tagValues = tagReferenceProperty.getValues();                
                for(int i=0; i<tagValues.length; i++) {
                    Node tagNode = this.node.getSession().getNodeByUUID(tagValues[i].getString());
                    CategoryItem tagItem = new CategoryItem(this.rulesRepository, tagNode);
                    returnList.add(tagItem);
                }
            }
            catch(PathNotFoundException e) {
                //the property doesn't even exist yet, so just return nothing
            }
            return returnList;
        }
        catch(Exception e) {
            log.error("Caught exception", e);
            throw new RulesRepositoryException(e);
        }
    }    
}
