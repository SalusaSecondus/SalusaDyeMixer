#!/usr/bin/perl

use common::sense;
use Data::Dumper;

my $multiline = '';
my $comment = '';

my @data;

my $working = {};
while (my $line = <>) {
    $line =~ s/\r\n/\n/g;
    chomp($line);
    print STDERR ">>$line\n";
    # print STDERR Dumper(\%working);

    # Split off comment
    if ($line =~ /^(.*)\s*\/\/(.*)$/) {
        print STDERR "Stripping comment\n";
        $line = $1;
        print STDERR ">>$line\n";
        if ($working->{'comment'}) {
            $working->{'comment'} .= " $2";
        } else {
            $working->{'comment'} = $2;
        }
    }

    if ($multiline) {
        my $last = 0;
        if ($line =~ /^(.*);$/) {
            $line = $1;
            $last = 1;
        }
        $working->{$multiline} .= " $line";
        if ($last) {
            $multiline = '';
        }
        next;
    }
    
    if ($line =~ /^\s*$/) {
        if ($working->{'type'}) {
            print STDERR "Saving object\n";
            print STDERR Dumper($working);

            push @data, $working;
            $working = {};
        }
        next;
    }

    if ($line =~ /^([a-z]+);$/) {
        # print STDERR "Setting mode\n";
        $working->{'type'} = $1;
        # $mode = $1;
        next;
    }

    if ($line =~ /^(.+)=\s*$/) {
        $multiline = $1;
        print STDERR "Multiline: $multiline\n";
        next;
    }
    if ($line =~ /^(.+)=\s*(\S.*,)\s*$/) {
        $multiline = $1;
        $working->{$multiline} = $2;
        print STDERR "Multiline2: $multiline\n";
        next;
    }

    if ($line =~ /^(.+)=(\S.*?);?$/) {
        print STDERR "Setting value >>$1<< = >>$2<<\n";
        $working->{$1} = $2;
        next;
    }
    die "Unhandled line: >>$line<<";
}
# print STDERR Dumper(\%data);

for my $item (@data) {
    print STDERR Dumper($item);
    my $spectrumCommand = '';
    my $type = $item->{'type'};
    if ($item->{'evendata'}) {
        my $spectrumData = $item->{'evendata'};
        my $spectrumStart = $item->{'start'};
        my $spectrumStep = $item->{'step'};
        $spectrumCommand = "var spectrum = new EvenlySampledSpectrum([$spectrumData], $spectrumStart, $spectrumStep);";
    } else {
        my $spectrumData = $item->{'unevendata'};
        $spectrumCommand = "var spectrum = new UnevenlySampledSpectrum([$spectrumData]);";
    }

    print "{\n";
    if ($item->{'comment'}) {
        print "    // " . $item->{'comment'} . "\n";
    }
    print "    $spectrumCommand\n";
    if ($type eq 'light') {
        my $lightName = $item->{'category'} . ", " . $item->{'name'};
        $lightName =~ s/"//g;
        print "    var light = new Light({name: \"$lightName\", spectrum: spectrum});\n";
        print "    availablelights.push(light);\n";
    } elsif ($type eq 'canvas') {
        my $canvasName = $item->{'category'} . ", " . $item->{'name'};
        $canvasName =~ s/"//g;
        print "    var canvas = new Canvas({name: \"$canvasName\", absorbancespectrum: spectrum});\n";
        print "    availablecanvases.push(canvas);\n";
    } elsif ($type eq 'dye') {
        my $name = $item->{'name'} || '""';
        my $ci = $item->{'ci'} || '""';
        my $category = $item->{'category'} || '""';
        my $mixture = $item->{'mixture'} || '""';
        print "    spectrum.clipNegatives();\n";
        print "    spectrum.normalizeAbsorbance();\n";
        print "    var dye = new Dye({name: $name, ci: $ci, category: $category, mixture: $mixture, absorbancespectrum: spectrum});\n";
        print "    availabledyes.push(dye);\n";
    } else {
        die "Unhandled type: $type";
    }
    print "}\n";
}