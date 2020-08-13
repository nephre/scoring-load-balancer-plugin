package jp.ikedam.jenkins.plugins.scoringloadbalancer.rules;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.queue.MappingWorksheet;
import hudson.util.FormValidation;
import hudson.util.RemotingDiagnostics;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringLoadBalancer;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.ScoringRule;
import jp.ikedam.jenkins.plugins.scoringloadbalancer.util.ValidationUtil;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerLoadScoringRule extends ScoringRule
{
    private static Logger LOGGER = Logger.getLogger(ServerLoadScoringRule.class.getName());
    private int scale;

    /**
     * @return the scale
     */
    public int getScale()
    {
        return scale;
    }

    @DataBoundConstructor
    public ServerLoadScoringRule(int scale)
    {
        this.scale = scale;
    }

    /**
     * Score the nodes.
     * <p>
     * Update scores by calling methods of nodesScore.
     * A node with a larger score is preferred to use.
     * <p>
     * If you want not to have scores updated with subsequent {@link ScoringRule}s, return false.
     *
     * @param task       the root task to build.
     * @param wc         Current work chunk (a set of subtasks that must run on the same node).
     * @param m          currently mapping status. there may be nodes already assigned.
     * @param nodesScore a map from nodes to their scores
     * @return whether to score with subsequent {@link ScoringRule}.
     * @throws Exception if any exception occurs, {@link ScoringLoadBalancer} falls back to a {@link LoadBalancer} registered originally.
     */
    @Override
    public boolean updateScores(Queue.Task task, MappingWorksheet.WorkChunk wc, MappingWorksheet.Mapping m, ScoringLoadBalancer.NodesScore nodesScore) throws Exception
    {
        String getSlaveLoad = "File file = new File('/proc/loadavg'); String load = file.text";
        String getCores = "def proc = \"nproc\".execute(); proc.waitFor(); println proc.in.text";

        for(Node node: nodesScore.getNodes())
        {
            int score;

            try {
                String loadString = RemotingDiagnostics.executeGroovy(getSlaveLoad, node.getChannel());
                String load1 = loadString.split(" ")[1].trim();
//                String load5 = loadString.split(" ")[2].trim();
//                String load15 = loadString.split(" ")[3].trim();
                String coreString = RemotingDiagnostics.executeGroovy(getCores, node.getChannel()).trim();

                float load = Float.parseFloat(load1);// * 15 + Float.parseFloat(load5) * 5 + Float.parseFloat(load15) + 1;
                int cores = Integer.parseInt(coreString);

                score = Math.round(cores - load);
            }
            catch (java.lang.NumberFormatException e)
            {
                // Windows node, or unable to determine load
                score = 0;
            }

            LOGGER.log(Level.INFO, String.format("%s got score %d", node.getDisplayName(), score));
            nodesScore.addScore(node, score * getScale());
        }

        return true;
    }

    /**
     * Manages views for {@link ServerLoadScoringRule}
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<ScoringRule>
    {
        /**
         * Returns the name to display.
         *
         * Displayed in System Configuration page, as a name of a scoring rule.
         *
         * @return the name to display
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName()
        {
            return Messages.ServerLoadScoringRule_DisplayName();
        }

        /**
         * Verify the input nodesPreferenceScale.
         *
         * @param value
         * @return
         */
        public FormValidation doCheckNodesPreferenceScale(@QueryParameter String value)
        {
            return ValidationUtil.doCheckInteger(value);
        }

        /**
         * Verify the input projectPreferenceScale.
         *
         * @param value
         * @return
         */
        public FormValidation doCheckProjectPreferenceScale(@QueryParameter String value)
        {
            return ValidationUtil.doCheckInteger(value);
        }
    }
}
