#!/usr/bin/perl
use strict;

# nasty mapping table
my $LMAP = {
	'el'    => 'el-rGR',
	'fa_IR' => 'fa-rIR',
	'zh_CN' => 'zh-rCN',
	'zh_TW' => 'zh-rTW',
	'pt_BR' => 'pt-rBR',
	'hu_HU' => 'hu',
	'it_IT' => 'it',
	'pl_PL' => 'pl',
	'id'    => 'in',
	'he_IL' => 'iw',
};


die "Stale 'translations'-dir exists\n" if -d 'translations';
system("tx pull -a --minimum-perc=30");

foreach my $src_file (glob("translations/vanilla-music-1.en-strings/*.xml")) {
	if ($src_file =~ /\/([a-zA-Z_]+)\.xml/) {
		my $locale = $1;
		$locale = $LMAP->{$locale} if exists $LMAP->{$locale};
		my $dst_file = "app/src/main/res/values-$locale/translatable.xml";

		warn "+ $src_file -> $dst_file\n";
		die "Unknown locale: '$locale', target=$dst_file\n" unless -f $dst_file;
		rename($src_file, $dst_file) or die "rename failed: $!\n";
	}
}

system("rm -rf translations");
