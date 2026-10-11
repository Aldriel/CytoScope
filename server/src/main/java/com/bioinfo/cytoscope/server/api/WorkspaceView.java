package com.bioinfo.cytoscope.server.api;

import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.scale.ScaleType;
import com.bioinfo.cytoscope.scale.Scales;
import com.bioinfo.cytoscope.server.workspace.Workspace;
import com.bioinfo.cytoscope.service.Example;
import java.util.List;

/**
 * A workspace as the web application sees it: the tubes loaded and the current set-up.
 *
 * @param id identifier to put in the next requests
 * @param example folder of the example it was opened from, or null
 * @param simulated true if the example data are simulated
 * @param language language of the names created by the server
 * @param timeToLiveMinutes minutes the workspace is kept after the last request
 * @param samples the tubes loaded
 * @param experiment the set-up
 */
public record WorkspaceView(String id, String example, boolean simulated, String language,
                            long timeToLiveMinutes, List<SampleView> samples,
                            ExperimentJson experiment) {

    /**
     * A loaded tube.
     *
     * @param id identifier
     * @param name name of the file, without extension
     * @param eventCount number of events
     * @param channels measured channels
     */
    public record SampleView(String id, String name, int eventCount, List<ChannelView> channels) {}

    /**
     * A measured channel.
     *
     * @param name name, such as {@code FITC-A}
     * @param label stain, such as {@code Annexin V}; may be empty
     * @param displayName name and stain, as shown to the user
     * @param range largest value
     * @param kind {@code scatter}, {@code time} or {@code fluorescence}
     * @param defaultScale scale used when the user has not chosen one
     */
    public record ChannelView(String name, String label, String displayName, double range,
                              String kind, ScaleType defaultScale) {

        /**
         * Describes a channel.
         *
         * @param channel the channel
         * @return its view
         */
        public static ChannelView of(Channel channel) {
            String kind = channel.isScatter() ? "scatter"
                    : channel.isTime() ? "time" : "fluorescence";
            return new ChannelView(channel.name(), channel.label(), channel.displayName(),
                    channel.range(), kind, Scales.defaultType(channel));
        }
    }

    /**
     * Describes a workspace.
     *
     * @param workspace the workspace
     * @param timeToLiveMinutes minutes it is kept after the last request
     * @return its view
     */
    public static WorkspaceView of(Workspace workspace, long timeToLiveMinutes) {
        List<SampleView> samples = workspace.samples().entrySet().stream()
                .map(e -> sample(e.getKey(), e.getValue())).toList();
        return new WorkspaceView(workspace.id(),
                workspace.example().map(Example::folder).orElse(null),
                workspace.example().map(Example::simulated).orElse(false),
                workspace.language(), timeToLiveMinutes, samples,
                ExperimentJson.of(workspace.experiment()));
    }

    private static SampleView sample(String id, FcsSample sample) {
        return new SampleView(id, sample.name(), sample.eventCount(),
                sample.channels().stream().map(ChannelView::of).toList());
    }
}
