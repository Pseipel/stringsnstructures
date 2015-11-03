package modules.suffixTree;

import java.util.Properties;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import common.parallelization.CallbackReceiver;
import modules.CharPipe;
import modules.InputPort;
import modules.ModuleImpl;
import modules.OutputPort;
import modules.suffixTree.output.SuffixTreeRepresentation;
import modules.suffixTree.suffixMain.GeneralisedSuffixTreeMain;
import modules.suffixTree.suffixTree.SuffixTree;
import modules.suffixTree.suffixTree.applications.ResultSuffixTreeNodeStack;
import modules.suffixTree.suffixTree.applications.ResultToRepresentationListener;
import modules.suffixTree.suffixTree.applications.SuffixTreeAppl;
import modules.suffixTree.suffixTree.applications.TreeWalker;
import modules.suffixTree.suffixTree.node.activePoint.ExtActivePoint;
import modules.suffixTree.suffixTree.node.info.End;
import modules.suffixTree.suffixTree.node.nodeFactory.GeneralisedSuffixTreeNodeFactory;

/**
 * Work in Progress.
 * 
 * Currently Reads KWIP processed text only, outputs a generalised suffix tree
 * based on the text. Does not handle units and types at the moment.
 * 
 * @author David Neugebauer
 */
public class GeneralisedSuffixTreeModule extends modules.ModuleImpl {

	private static final Logger LOGGER = Logger.getLogger(GeneralisedSuffixTreeMain.class.getName());

	// Variables for the module
	private static final String MODULE_NAME = "GeneralisedSuffixTreeModule";
	private static final String MODULE_DESCRIPTION = "Reads from KWIP modules output and constructs output suitable for clustering.";

	// Variables describing I/O
	private static final String INPUT_TEXT_ID = "plain";
	private static final String INPUT_TEXT_DESC = "[text/plain] Takes a plaintext representation of the KWIP result.";
	private static final String OUTPUT_ID = "json";
	private static final String OUTPUT_DESC = "[text/json] A json representation of the tree build, suitable for clustering.";

	// Variables for input processing
	private static final char TERMINATOR = '$';

	public GeneralisedSuffixTreeModule(CallbackReceiver callbackReceiver, Properties properties) throws Exception {
		// Call parent constructor
		super(callbackReceiver, properties);

		// Set the modules name and description
		this.getPropertyDefaultValues().put(ModuleImpl.PROPERTYKEY_NAME, MODULE_NAME);
		this.setDescription(MODULE_DESCRIPTION);

		// Setup I/O, reads from char input produced by KWIP,
		InputPort inputTextPort = new InputPort(INPUT_TEXT_ID, INPUT_TEXT_DESC, this);
		inputTextPort.addSupportedPipe(CharPipe.class);
		OutputPort outputPort = new OutputPort(OUTPUT_ID, OUTPUT_DESC, this);
		outputPort.addSupportedPipe(CharPipe.class);
		super.addInputPort(inputTextPort);
		super.addOutputPort(outputPort);
	}

	@Override
	public boolean process() throws Exception {
		try {
			// read the whole text once, neccessary to know the text's length
			final String text = readTextInput(this.getInputPorts().get(INPUT_TEXT_ID));

			// set some static variables to regulate flow in SuffixTree classes
			SuffixTreeAppl.unit = 0;
			SuffixTree.oo = new End(Integer.MAX_VALUE / 2);

			// The suffix tree used to read the input is a generalised
			// suffix
			// tree for a text of the length of the input string
			final SuffixTreeAppl suffixTreeAppl = new SuffixTreeAppl(text.length(),
					new GeneralisedSuffixTreeNodeFactory());

			// start and end indices regulate which portion of the input we are
			// reading at any given moment
			int start = 0;
			int end = text.indexOf(TERMINATOR, start);

			if (end != -1) {
				// traverse the first portion of the input string
				// TODO comment explaining why extActivePoint has to be null
				// here
				suffixTreeAppl.phases(text, start, end + 1, null);
				start = end + 1;

				// traverse the remaining portions of the input string
				ExtActivePoint extActivePoint;
				String nextText;
				start = end + 1;
				end = text.indexOf(TERMINATOR, start);
				while (end != -1) {
					// each cycle represents a text read
					SuffixTreeAppl.textNr++;

					// TODO comment explaining what setting the active point
					// does
					nextText = text.substring(start, end + 1);
					extActivePoint = suffixTreeAppl.longestPath(nextText, 0, 1, start, true);
					if (extActivePoint == null) {
						LOGGER.warning(" GeneralisedSuffixTreeMain activePoint null");
						break;
					}

					// TODO comment explaining the use of .oo and extActivePoint
					// why has this to happen here instead of inside phases() ?
					SuffixTree.oo = new End(Integer.MAX_VALUE / 2);
					suffixTreeAppl.phases(text, start + extActivePoint.phase, end + 1, extActivePoint);

					// reset text window for the next cycle
					start = end + 1;
					end = text.indexOf(TERMINATOR, start);
				}
			} else {
				LOGGER.warning("Did not find terminator char: " + TERMINATOR);
			}

			// construct the JSON output and write it to the output port
			final String ouput = generateJsonOutput(suffixTreeAppl);
			this.getOutputPorts().get(OUTPUT_ID).outputToAllCharPipes(ouput);

		// no catch block, this should just crash on error
		} finally {
			this.closeAllOutputs();
		}

		return true;
	}

	// simply reads the whole input of inputPort once and returns it as a string
	private String readTextInput(InputPort inputPort) throws Exception {
		StringBuilder totalText = new StringBuilder();
		int charCode = inputPort.getInputReader().read();
		while (charCode != -1) {
			if (Thread.interrupted()) {
				throw new InterruptedException("Thread has been interrupted.");
			}
			totalText.append((char) charCode);
			charCode = inputPort.getInputReader().read();
		}
		return totalText.toString();
	}

	private String generateJsonOutput(SuffixTreeAppl suffixTreeAppl) {
		// apparently this needs to be statically for any result listener to
		// work correctly
		ResultSuffixTreeNodeStack.suffixTree = suffixTreeAppl;

		// build an object to hold a representation of the tree for output
		// and add it's nodes via a listener.
		final SuffixTreeRepresentation suffixTreeRepresentation = new SuffixTreeRepresentation();
		final ResultToRepresentationListener listener = new ResultToRepresentationListener(suffixTreeAppl,
				suffixTreeRepresentation);
		final TreeWalker treeWalker = new TreeWalker();
		suffixTreeRepresentation.setUnitCount(0);
		suffixTreeRepresentation.setNodeCount(suffixTreeAppl.getCurrentNode());
		treeWalker.walk(suffixTreeAppl.getRoot(), suffixTreeAppl, listener);

		// serialize the representation to JSON
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		final String output = gson.toJson(suffixTreeRepresentation);
		return output;
	}

	@Override
	public void applyProperties() throws Exception {
		super.setDefaultsIfMissing();
		super.applyProperties();
	}
}