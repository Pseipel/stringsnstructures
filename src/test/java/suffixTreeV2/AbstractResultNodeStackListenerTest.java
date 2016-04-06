package suffixTreeV2;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import org.junit.Test;

import modules.suffixTreeV2.AbstractResultNodeStackListener;
import modules.suffixTreeV2.BaseSuffixTree;
import modules.suffixTreeV2.GST;
import modules.suffixTreeV2.Node;
import modules.suffixTreeV2.TreeWalker;

public class AbstractResultNodeStackListenerTest {

	/**
	 * Inner class used to test the abstract class's mechanisms.
	 */
	private class TestListener extends AbstractResultNodeStackListener {

		final BaseSuffixTree tree;

		// the amount of nodes processed by this listener
		int nodesProcessed = 0;

		TestListener(BaseSuffixTree tree) {
			super(tree);
			this.tree = tree;
		}

		public void process(final Node node, int level) {
			final Stack<Node> nodes = super.getNodes();

			// test that the level reported by the listener matches the amount
			// of nodes on the stack (assumes that listening started on the
			// root)
			assertEquals(nodes.size(), level);

			// check that each node is a child of the previous node on the stack
			// up until the current node
			Node current = null;
			Node expectedNext = null;
			Node next = null;
			char edgeBegin = '\0';
			for (int i = 0; i < nodes.size() - 1; i++) {
				current = nodes.get(i);
				expectedNext = nodes.get(i + 1);
				assertNotNull(current);
				assertNotNull(expectedNext);

				edgeBegin = tree.edgeString(expectedNext).charAt(0);
				next = tree.getNode(current.getNext(edgeBegin));

				assertNotNull(next);
				assertEquals(expectedNext, next);
			}

			// the last node on the stack should be the parent of the node being
			// processed
			if (nodes.size() > 0) {
				edgeBegin = tree.edgeString(node).charAt(0);
				assertEquals(tree.getNode(nodes.peek().getNext(edgeBegin)), node);
			}
			// if no node is left on the stack, root is the node being processed
			else {
				assertEquals(tree.getNode(tree.getRoot()), node);
			}

			// compare the set of leaves found by travelling down the edges to
			// the set aggregated by the node stack listener
			final Set<Node> expectedLeaves = findLeaves(node);
			assertTrue(expectedLeaves.equals(node.getLeaves()));

			// don't forget to increment the amount of nodes processed
			nodesProcessed += 1;
		}

		private Set<Node> findLeaves(Node node) {
			Set<Node> result = new HashSet<Node>();
			findLeaves(node, result, node);
			return result;
		}

		private void findLeaves(Node current, Set<Node> leaves, Node initial) {
			for (char c : current.getEdgeBegins()) {
				findLeaves(tree.getNode(current.getNext(c)), leaves, initial);
			}
			if (current.isTerminal() && current != initial) {
				leaves.add(current);
			}
		}

	}

	@Test
	public void test() {
		final String input = "aa bb acd$bb acd aa$Petra liest das Buch$Maria liest das Buch$mississippi$romane$romanus$romulus$rubens$ruber$rubicon$rubicundus$";
		BaseSuffixTree tree = null;

		// just build the generalised suffix tree
		try {
			tree = GST.buildGST(new StringReader(input), null);
		} catch (Exception e) {
			fail("Failed to build the generalised suffix tree.");
		}

		TestListener listener = new TestListener(tree);

		// the main testing is done by walking the tree and triggering the tests
		// in TestListener.process()
		try {
			TreeWalker.walk(tree.getRoot(), tree, listener);
		} catch (IOException e) {
			fail("Mysteriously this raised an IOException without IO happening.");
		}

		// test that all nodes were processed
		assertEquals(tree.getNodeAmount(), listener.nodesProcessed);
	}

}